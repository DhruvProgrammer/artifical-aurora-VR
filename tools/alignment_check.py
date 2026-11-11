#!/usr/bin/env python3
"""Measure frame-to-frame background motion in an Aurora AR clip.

The v5 prompt requires a debug world-grid overlay and a script that tracks
static background features with optical flow, then compares the grid's motion
against them (target: <= 3 px reprojection error during 60 deg/s pans, no
cumulative drift over 60 s).

This build does not render that debug grid, and its aurora is screen-space
rather than world-locked, so there is no grid motion to compare. This script is
the measurement half of that test: it reports the background motion that a
future world-grid overlay must match.

Requirements
------------
Python 3.10+ and OpenCV (not installed or vendored by this repo):

    pip install opencv-python numpy

Run
---
    python tools/alignment_check.py clip.mp4
    python tools/alignment_check.py clip.mp4 --csv motion.csv
    python tools/alignment_check.py clip.mp4 --max-frames 900 --scale 0.5

Output
------
Per consecutive frame pair, an affine partial transform is estimated with ORB
features and RANSAC. The script prints median / p95 / max translation and
rotation, and writes a CSV when requested. A world-grid overlay should move by
the same transform as the background; compare the grid corners against these
values to obtain the reprojection error.

Exit codes: 0 = measured, 1 = unreadable video / too few features, 2 = missing
OpenCV.
"""

from __future__ import annotations

import argparse
import csv
import math
import sys
from pathlib import Path

try:
    import cv2  # type: ignore
    import numpy as np  # type: ignore
except ImportError:
    print(
        "OpenCV is required: pip install opencv-python numpy\n"
        "This is an optional offline tool; nothing is installed by the app build.",
        file=sys.stderr,
    )
    raise SystemExit(2)


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, round((pct / 100.0) * (len(ordered) - 1))))
    return ordered[index]


def measure(
    video: Path,
    max_frames: int,
    scale: float,
) -> tuple[list[dict[str, float]], float]:
    capture = cv2.VideoCapture(str(video))
    if not capture.isOpened():
        raise SystemExit(f"Could not open {video}")

    fps = capture.get(cv2.CAP_PROP_FPS) or 30.0
    orb = cv2.ORB_create(nfeatures=1500)
    matcher = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=True)

    rows: list[dict[str, float]] = []
    previous_gray = None
    index = 0

    while index < max_frames:
        ok, frame = capture.read()
        if not ok:
            break
        if scale != 1.0:
            frame = cv2.resize(frame, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

        if previous_gray is not None:
            kp1, des1 = orb.detectAndCompute(previous_gray, None)
            kp2, des2 = orb.detectAndCompute(gray, None)
            if des1 is not None and des2 is not None and len(kp1) >= 8 and len(kp2) >= 8:
                matches = matcher.match(des1, des2)
                matches = sorted(matches, key=lambda m: m.distance)[:400]
                if len(matches) >= 8:
                    src = np.float32([kp1[m.queryIdx].pt for m in matches]).reshape(-1, 1, 2)
                    dst = np.float32([kp2[m.trainIdx].pt for m in matches]).reshape(-1, 1, 2)
                    matrix, inliers = cv2.estimateAffinePartial2D(
                        src,
                        dst,
                        method=cv2.RANSAC,
                        ransacReprojThreshold=3.0,
                        maxIters=2000,
                        confidence=0.995,
                    )
                    if matrix is not None:
                        dx = float(matrix[0, 2])
                        dy = float(matrix[1, 2])
                        a = float(matrix[0, 0])
                        b = float(matrix[0, 1])
                        rotation = math.degrees(math.atan2(b, a))
                        scale_est = math.hypot(a, b)
                        inlier_count = int(inliers.sum()) if inliers is not None else 0
                        rows.append(
                            {
                                "frame": float(index),
                                "time_s": float(index / fps),
                                "dx_px": dx,
                                "dy_px": dy,
                                "translation_px": math.hypot(dx, dy),
                                "rotation_deg": rotation,
                                "scale": scale_est,
                                "inliers": float(inlier_count),
                            }
                        )
        previous_gray = gray
        index += 1

    capture.release()
    return rows, fps


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("video", type=Path, help="video file to analyse")
    parser.add_argument("--csv", type=Path, help="write per-frame rows to CSV")
    parser.add_argument("--max-frames", type=int, default=1800, help="frames to read (default 1800)")
    parser.add_argument("--scale", type=float, default=1.0, help="resize factor before feature detection")
    args = parser.parse_args()

    rows, fps = measure(args.video, args.max_frames, args.scale)
    if not rows:
        print("Too few trackable frames/features; nothing to report.", file=sys.stderr)
        return 1

    translations = [row["translation_px"] for row in rows]
    rotations = [abs(row["rotation_deg"]) for row in rows]
    cumulative_x = sum(row["dx_px"] for row in rows)
    cumulative_y = sum(row["dy_px"] for row in rows)

    print(f"video: {args.video}")
    print(f"fps: {fps:.2f}  analysed pairs: {len(rows)}")
    print(f"translation px  median={percentile(translations, 50):.2f}  "
          f"p95={percentile(translations, 95):.2f}  max={max(translations):.2f}")
    print(f"rotation deg    median={percentile(rotations, 50):.3f}  "
          f"p95={percentile(rotations, 95):.3f}  max={max(rotations):.3f}")
    print(f"sum(dx)={cumulative_x:.1f} px  sum(dy)={cumulative_y:.1f} px")
    print(
        "\nNo debug grid is rendered by this build, so these numbers describe the "
        "background only. A world-locked grid must match them within 3 px during pans."
    )

    if args.csv:
        args.csv.parent.mkdir(parents=True, exist_ok=True)
        with args.csv.open("w", newline="", encoding="utf-8") as handle:
            writer = csv.DictWriter(
                handle,
                fieldnames=[
                    "frame",
                    "time_s",
                    "dx_px",
                    "dy_px",
                    "translation_px",
                    "rotation_deg",
                    "scale",
                    "inliers",
                ],
            )
            writer.writeheader()
            writer.writerows(rows)
        print(f"wrote {args.csv}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
