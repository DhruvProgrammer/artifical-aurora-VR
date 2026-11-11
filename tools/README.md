# Tools

Optional offline helpers. Nothing here is part of the Android build.

## `alignment_check.py`

Tracks static background features between consecutive frames with ORB + RANSAC
and reports translation and rotation. It is the measurement half of the v5
alignment test.

**Honest limitation:** this build does not render the required debug world-grid
overlay, and the aurora is screen-space rather than world-locked. The script
therefore reports background motion only; there is no grid transform to compare.
Do not claim the ≤ 3 px alignment target until the overlay exists and a clip is
recorded and analysed.

See the docstring for setup and usage. Optional dependencies are documented in
`DEPENDENCIES.md` and are not installed.
