# Recording performance, thermals and battery

Status date: **2026-11-13**. One 10-second Free take ran on a Samsung Galaxy S10
(`SM-G973N`, Android 12) at 720p, so the file is known to encode and finalise.
It contains **92 frames over 10.13 s (≈9.1 fps)**, with most frame gaps exactly
100 ms — consistent with a 10 fps low-light camera mode, not a measured
compositor cost. That single dark take still gives **no dropped-frame,
A/V-drift, temperature or battery numbers**, and its 10.13 s length is one
sample, not a duration-accuracy result. The values below are the v5 targets and
a static breakdown of what the code does. Treat every number as a budget to
verify, not a result.

---

## 1. Targets from the v5 prompt

| Metric | Target |
|---|---|
| Preview during recording | ≥ 30 FPS on the reference device |
| A/V drift | < 40 ms over a 5-minute take |
| Duration accuracy | ± 100 ms |
| Timestamps | monotonic, no duplicate/negative PTS |
| Free limit | 10.000 s (encoder stop latency may overshoot) |
| Thermal | clean stop at `CRITICAL`, warn at `MODERATE` |
| Battery | warn < 15%, refuse < 5% unplugged |
| Storage | warn < 500 MB, refuse < 100 MB |

## 2. Static per-frame work

### Analysis path (5 Hz, not per rendered frame)

- CameraX `ImageAnalysis` at **640×480 YUV_420_888**,
  `STRATEGY_KEEP_ONLY_LATEST`.
- Feature grid **160×90 = 14,400 cells**; colour statistics only on sky-masked
  cells. Working `FloatArray`s are preallocated.
- `SkyScoreEngine` runs a phase classifier, formulas and EMA at 5 Hz (200 ms).
  Solar position refreshes every 30 s; location every 10 min.
- The analysis use case reads the raw camera stream, not the composited output,
  so it never analyses the aurora overlay.

### Composite path (per camera frame)

- One OES camera texture, one full-screen camera quad, one full-screen aurora
  quad (up to **four** style layers in the fragment shader), one watermark quad.
- Output is the encoder size: **1280×720 Free**, **1920×1080 Premium**.
- One draw feeds both the preview and the encoder
  (`OUTPUT_OPTION_ONE_FOR_ALL_TARGETS`), at the cost of one extra GPU copy.
- No aurora FBO ring, no post-processing chain, no raymarching.
- Aurora time comes from the recording clock during a take, so pause/resume does
  not jump the animation.

### On-demand costs

| Action | Cost |
|---|---|
| Photo | `glReadPixels` of the full composite: ~3.7 MB at 720p, ~8.3 MB at 1080p, then row flip into an `IntArray` and JPEG encode on `Dispatchers.IO`. Expect a one-frame hitch. |
| Gallery open | MediaStore query + one `MediaMetadataRetriever` thumbnail per visible row, all on `Dispatchers.IO`. |
| Capability probe | One `MediaCodecList` scan at camera-screen creation. |
| Orphan cleanup | One MediaStore query and one directory listing at launch, on `Dispatchers.IO`. |
| Style preview | Same composite cost; only uniforms change. No extra allocation. |

## 3. What could break the budget (hypotheses to test)

1. **Four-layer Premium shader at 1080p** on a mid-range GPU. Each layer evaluates
   value-noise/fBm; this is the most likely FPS cliff.
2. **Preview + encoder sharing one output** adds a GPU copy and can force the
   preview to the encoder resolution.
3. **Photo readback** stalls the GL thread for one frame at 1080p.
4. **Thermal throttling** on a 30-minute Premium soak; CameraX and the platform
   may reduce camera FPS before the app sees an error.
5. **MediaStore finalisation** latency at stop, which affects the Free 10 s
   accuracy claim.

## 4. How to measure on a device

### FPS and frame timing

```powershell
adb shell dumpsys gfxinfo com.auroraar.debug reset
# record for 60 s, then
adb shell dumpsys gfxinfo com.auroraar.debug
adb shell dumpsys SurfaceFlinger --latency <layer-name>
```

Or capture a Perfetto trace:

```powershell
adb shell perfetto -o /data/misc/perfetto-traces/aurora.perfetto-trace -t 30s \
  sched freq idle am wm gfx view binder_driver hal camera video
adb pull /data/misc/perfetto-traces/aurora.perfetto-trace
```

### Thermal and battery

```powershell
adb shell dumpsys thermalservice
adb shell dumpsys battery
adb shell dumpsys batterystats --reset
# run the soak, then
adb shell dumpsys batterystats com.auroraar.debug
```

### A/V drift and duration

Record a clap/flash at the start and end of a 5-minute take. Extract timestamps:

```powershell
ffprobe -v error -show_entries stream=index,codec_type,duration,start_time `
  -show_entries format=duration -of json clip.mp4
ffmpeg -i clip.mp4 -af "silencedetect=n=-35dB:d=0.1" -f null -
```

Compare the first/last visual event with the audio samples. Target < 40 ms.

### File validity

```powershell
ffprobe -v error -show_streams -show_format clip.mp4
ffmpeg -v error -i clip.mp4 -f null -
```

### Orphan state after a kill

```powershell
adb shell content query --uri content://media/external/video/media `
  --projection _id:_display_name:is_pending:relative_path:owner_package_name
```

Kill the app during a take, relaunch, and confirm no row for this package keeps
`is_pending=1` older than 60 s.

## 5. Devices required for a real report

The v5 prompt asks for at least three:

1. mid-range Snapdragon 6xx/7xx or Dimensity (reference),
2. low-RAM device,
3. flagship that passes the 4K gate (this build does not expose 4K).

Until those runs exist, the honest summary is: **one 720p take on one Android 12
phone; FPS, drift, thermals and battery still unmeasured**.

### What the first take did show

| Observation | Value |
|---|---|
| Container / codec | MP4, H.264/AVC, one video track, no audio track |
| Resolution | 720×1280 (portrait), identity display matrix |
| Duration | 10.13 s for a 10 s Free limit (+130 ms encoder latency) |
| File size | 14,761,799 bytes (≈11.7 Mbit/s) |
| Frame rate | 92 frames / 10.13 s ≈ 9.1 fps; 68 of 91 gaps are exactly 100 ms, so likely a 10 fps low-light camera mode. Not a compositor benchmark |
| Thermal / battery | Battery warning at 7–8 % appeared; the phone later powered off |
