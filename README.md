# Aurora AR — SkyScore

An Android camera app that overlays a simulated aurora on the real sky and adapts it to what the camera can actually see: how dark it is, how cloudy, how much open sky is in frame, and how much light pollution is around.

This repo implements the **SkyScore & Adaptive Intensity Engine** (addendum v4). The companion v2/v3 documents — the full AR aurora renderer and the semantic sky-mask/occlusion engine — are not part of this repo, so a small procedural GL renderer and a classical sky mask stand in for them. Both substitutions are marked below and in the code.

> SkyScore rates how suitable the current sky is for this **simulated** experience.
> It is **not** an aurora forecast: no Kp index, no solar wind, no space-weather data, no network.

## What works

- Live camera preview with a procedural aurora composited on top (GLSL, translucent surface).
- SkyScore 0–100 from four measured sub-scores: ambient brightness, cloud, sky visibility, environmental lighting.
- Phase classification (daylight / sunset / twilight / night) fusing **four independent cues**: photometric sky luminance from the exposure triplet, sky appearance, ambient light sensor, and an offline sun-position prior. Sensor noise/ISO is a fifth cue.
- Hysteresis so the phase does not flicker, and asymmetric smoothing so the score rises slowly and falls quickly.
- Adaptive aurora: brightness, saturation, curtain density, glow, ray contrast, animation speed, sharpness, stars, cloud attenuation and a per-pixel sky-luminance gate.
- Auto / Creative / Off modes. Creative bypasses the daylight gate with a visible "simulated" badge and a dusk relight.
- SkyScore chip + detail sheet (ring gauge, four rows, "why", honest info copy).
- Daylight card, condition messages, English + Hindi.
- Calibration recorder: one labelled sample per second, JSON export + share, for tuning on real phones.
- Everything on-device. The manifest has **no `INTERNET` permission**.

## Pipeline

```
CameraX frame (640x480 YUV)
  └─ 160x90 grid → sky mask (tilt prior × colour plausibility, eroded)
       ├─ sky luminance / colour / gradient / noise / stars
       └─ foreground luminance + bright blobs
Exposure triplet (Camera2 CaptureResult)  ─┐
Ambient light sensor                       ├─► BrightnessEstimator → PhaseClassifier → hysteresis
Offline sun position (time + optional loc) ┘
                                              └─► CloudEstimator (sky mask only) → SkyScore → IntensityMapper
                                                                                        └─► GL uniforms
```

## Spec coverage

| Addendum | Status |
|---|---|
| §4 brightness fusion, AE handling | Implemented (`BrightnessEstimator`, AE hold in `SkyScoreEngine`) |
| §5 phase hysteresis + darkness anchors | Implemented, tested |
| §6 cloud model | Classical features only; no learned cloud head, no per-pixel density map |
| §6.5 world-fixed sky map | Not implemented |
| §7 visibility | Implemented |
| §8 lighting | Implemented (glow, blobs, foreground, sensor, median filter) |
| §9 SkyScore formula | Implemented, §9.3 table covered by JVM tests |
| §10 adaptive rendering | Implemented as shader uniforms; per-pixel sky gate uses a global luminance gradient |
| §11 temporal dynamics | Implemented (EMA + asymmetric score + 1.2 s render smoothing) |
| §12 modes / messages / UI | Implemented, EN + HI |
| §13 code structure | `com.auroraar.skyscore.*` mirrors the spec |
| §14 performance | 10 Hz analysis, 160×90 readback, ~⅔-res GL surface |
| §15 calibration + tests | Recorder + JVM tests; no labelled field data yet |
| §17 honest limits | See below |

**Substitutions:** v3's semantic sky mask and ARCore depth occlusion are replaced by a tilt-aware classical mask; v2's full aurora renderer is replaced by a compact procedural shader. Expect the mask to confuse dark silhouettes above the horizon, and the renderer to be simpler than the v2 design.

## Honest limits

- Brightness from a phone camera is an estimate (± ~1 EV). HDR / night modes and tone-mapping distort it, so several cues are fused and confidence is reported.
- Cloud detection at night is weak. Stars are positive evidence of clear sky; absence of stars is **not** evidence of cloud, so the estimate shrinks toward a neutral prior and the UI says so.
- Light-sensor readings depend on where the sensor sits and how the phone is held.
- Thresholds are the addendum's starting values, not tuned values. The calibration recorder exists so they can be fitted to real phones.
- SkyScore is a visual-suitability rating, not an aurora forecast.

## Build

The toolchain lives inside this folder under `.toolchain/` (see `DEPENDENCIES.md`):

```powershell
$env:GRADLE_USER_HOME = "$PWD\.toolchain\gradle-home"
$env:ANDROID_HOME     = "$PWD\.toolchain\android-sdk"
.\gradlew.bat testDebugUnitTest assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

Without the local toolchain, install Android SDK platform 35 + build-tools 35.0.0 and run `./gradlew assembleDebug`, or push to GitHub — `.github/workflows/android.yml` builds and uploads the debug APK.

## Tests

`./gradlew testDebugUnitTest` covers:

- the §9.3 intensity table rows and the 92/100 illustrative example
- monotonicity in cloud cover and darkness
- gate precedence (no-sky / daylight / creative bypass)
- phase fusion (day, sunset, twilight, night, dark override)
- hysteresis timing and no-oscillation on a noisy signal
- intensity mapping (daylight off, stars fade under cloud, max-intensity cap)
- message policy (Creative daylight never claims the sky is dark)
- sun position for Jaipur at noon and midnight
- end-to-end engine: daylight blocked, dark clear night converges ≥ 75, off mode disables rendering

## Layout

```
app/src/main/java/com/auroraar/
├── skyscore/       engine, models, policy, estimators (pure Kotlin)
├── camera/         CameraX session, feature extraction, exposure/lux/pose/location readers
├── render/         procedural aurora GL view + shader
├── calibration/    JSON sample recorder
├── data/           settings
└── ui/             Compose screens, sheets, theme
```

## Privacy

No INTERNET permission, no analytics, no uploads. Coarse location is optional and used only to refine the offline sun position; calibration JSON stays in app storage until the user shares it.

## License

TBD.
