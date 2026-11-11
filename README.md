# Aurora AR — SkyScore

An Android app that paints a fake aurora over your live camera picture. It watches the sky through the camera and changes the aurora to match: brighter on a dark clear night, faint at dusk, gone in daylight.

The aurora is **simulated**. The app does not know whether a real aurora is happening. It never fetches space weather, because it has no internet permission at all.

---

## Part 1 — What it does, in plain English

### The basic idea

Point your phone at the sky. The app looks at what the camera sees and asks four simple questions:

1. **Is it dark enough?** Daytime sky is too bright for an aurora to make sense.
2. **Is it cloudy?** Clouds hide the sky, so the aurora gets weaker.
3. **Can it see open sky?** If you are pointing at a wall, a tree, or the ground, there is nothing to draw on.
4. **Is there light pollution?** Streetlights and city glow wash the sky out, so the aurora is toned down a little.

It turns the answers into a score from **0 to 100**. That score is the SkyScore. The higher the score, the stronger the aurora is drawn.

### What you see on screen

- A **live camera preview**, full screen.
- The **aurora** drawn on top: green and violet curtains that drift slowly. A few stars appear on clear nights.
- A small **score chip** in the corner. Tap it and a panel opens with the score, the four ingredients, and a short line explaining the result.
- Short messages when the aurora is off, for example "It's daytime! The aurora shown here is simulated and looks best after sunset." or "Point your camera at the open sky".

The score has six labels:

| Score | Label | What it means |
|---|---|---|
| 90–100 | Excellent conditions | Dark, clear, lots of sky |
| 70–89 | Great conditions | Good conditions |
| 50–69 | Good conditions | Usable, with some cloud or glow |
| 30–49 | Fair conditions | Faint aurora at best |
| 1–29 | Limited conditions | Barely visible |
| 0 | Unavailable | Daylight, no sky, or camera covered |

### Three modes

- **Auto** — the app decides. The aurora follows the score. In daylight it switches itself off and says why.
- **Creative** — you decide. The aurora stays on even in daylight, with a "simulated" badge and a dusk colour grade so it does not look absurd. You can still lock it to full strength or let it follow the score. Creative still refuses to draw when there is no sky or the camera is covered.
- **Off** — camera preview only, no aurora.

There are also sliders for overall intensity, colour saturation, and a maximum cap, plus a switch for whether clouds should weaken the aurora in Creative mode.

### The important honest part

**SkyScore is not an aurora forecast.** It does not tell you whether aurora will appear tonight. It only rates how good the current view is for showing a *simulated* aurora. A score of 95 means "this sky would look great with the effect", not "go outside, aurora is coming".

The app also cannot work miracles:

- A phone camera in the dark is noisy. Brightness is an estimate, not a light meter.
- Night clouds are hard to judge. Stars prove the sky is clear; a lack of stars proves nothing, because the camera may simply not see them.
- The light sensor sits wherever the manufacturer put it, so it is only one clue among several.
- A covered lens and a genuinely featureless dark sky look similar. The mask rejects flat neutral frames, so a perfectly neutral dark overcast night may be reported as "no sky" instead of "cloudy".
- The score thresholds are sensible starting values, not values tuned on thousands of real phones. A calibration recorder is built in so that can be fixed later with real data.

### What you need

- An Android phone with a camera, running **Android 8.0 (API 26)** or newer.
- Camera permission. Location permission is optional: if you allow it, the app can place the sun more accurately for the time of day. If you refuse, it falls back to your time zone and still works.

### Install the app

Download the debug APK from the latest GitHub Actions run on `main` (artifact `aurora-ar-debug-apk`), or build it yourself:

```powershell
$env:GRADLE_USER_HOME = "$PWD\.toolchain\gradle-home"
$env:ANDROID_HOME     = "$PWD\.toolchain\android-sdk"
.\gradlew.bat assembleDebug
```

Then:

```powershell
.\.toolchain\android-sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

The APK lands at `app/build/outputs/apk/debug/app-debug.apk`. Everything is debug-signed; there is no Play Store release, and the version is `0.1.0`.

### Is my data safe?

Yes, in the sense that nothing leaves the phone. The app:

- has **no `INTERNET` permission**, so it physically cannot upload anything,
- has no analytics and no ads,
- asks for coarse location only to improve the sun position, and it is optional,
- writes calibration recordings only to the app's own storage, and only shares them if you press Share,
- has Android backup and device-transfer extraction disabled, so its settings and recordings are not copied off the phone by the system either.

---

## Part 2 — Technical reference

Everything below this line is implementation detail. It assumes you know Android and some signal processing.

### Project provenance

This repo implements the **SkyScore and Adaptive Intensity Engine** from an addendum labelled v4. The companion v2 and v3 documents (the full AR aurora renderer and the semantic sky-mask/occlusion engine) were not available, so two parts are substitutes, not ports:

- **Renderer** — a compact procedural GLSL shader instead of the v2 aurora renderer.
- **Sky mask** — a classical tilt-and-colour mask instead of v3's semantic segmentation and ARCore depth occlusion.

Both are flagged in code and in the coverage table below.

### Signal pipeline

```
CameraX ImageAnalysis  640x480 YUV_420_888, KEEP_ONLY_LATEST, ~5 Hz
  └─ SkyFeatureExtractor
       ├─ downsample to 160x90
       ├─ limited-range YUV expanded so black = 0 and white = 1
       ├─ sky mask = vertical/tilt prior × colour plausibility, eroded
       ├─ mask stats: fSky, meanLum, skyMeanLinear, blueDominance, warmth,
       │              saturation, textureEnergy, horizon/zenith gradient, noiseSigma, stars
       └─ foreground stats: mean luminance, bright blobs
ExposureReader (Camera2 CaptureResult: t, ISO, f-number, AE comp, AE converged)
LightSensorReader (TYPE_LIGHT, optional)
PoseReader (gravity/accelerometer → camera elevation)
LocationProvider (coarse, optional)
AstronomyPrior (offline NOAA solar elevation)
        │
        ▼
SkyScoreEngine.onInput(EngineInput)          ← analysis thread
  ├─ BrightnessEstimator  → logLum + confidence
  ├─ PhaseClassifier.fuse → per-phase probabilities
  ├─ PhaseHysteresis      → stable Phase
  ├─ SkyScoreFormula      → darkness, sub-scores, gate, score
  ├─ CloudEstimator       → cover, confidence, class
  ├─ VisibilityEstimator  → fSky × tilt gate
  ├─ LightingEstimator    → light pollution, 7-sample median
  └─ IntensityMapper      → AuroraRenderParams
        │
        ▼
StateFlow<SkyConditions> + StateFlow<AuroraRenderParams>
  ├─ Compose UI (chip, sheet, settings, calibration)
  └─ AuroraGlView (GLES2, uniforms)
```

The engine is pure Kotlin with no Android imports, which is why the whole scoring path is testable on the JVM.

### Brightness estimation

Photometric path, when the device exposes a Camera2 capture result:

```
L_sky ≈ (12.5 · N² / (t · ISO)) · (Y_sky / 0.16) · 2^(−AE compensation)
logLum = log10(L_sky), clamped to [−4, 5]
```

`Y_sky` is the linearised mean of sky-masked pixels. If the mean is below `0.01` or above `0.95` the pixels are crushed or clipped and the photometric result is discarded. Confidence starts at `0.85` and halves when AE has not converged.

When metadata is missing or unusable, an appearance fallback scores cues that do not depend on absolute brightness: star count (≥25 → −1.9, ≥8 → −1.2, ≥1 → −0.3 log luminance), warm sunset colour (+2.1), bright frame (+2.6), blue dusk (+0.8), dark sky (−1.0), otherwise +1.2. Brightness is checked before blueness so a clear midday sky is never mistaken for dusk. Its confidence is fixed at `0.32`, so it can steer the phase but never dominate.

The engine also implements AE awareness: if `|Δ logExposure| > 0.3` between frames, the previous luminance estimate is held for **400 ms** and its confidence is halved, so an exposure hunt does not drag the phase around.

Temporal smoothing uses an exponential filter with `τ = 0.6 s`, dropping to `τ = 0.25 s` when the new sample is more than 1.5 log units away from the smoothed value.

### Phase classification

Four phases, indexed `DAYLIGHT, SUNSET, TWILIGHT, NIGHT`, with darkness anchors `[0.00, 0.20, 0.375, 1.00]`.

Cues are fused as per-class log-likelihoods, and the softmax gives a probability distribution:

| Cue | Weight | Notes |
|---|---|---|
| Photometric luminance | `1.0 × confidence` | Gaussian around per-phase means `[3.4, 2.2, 0.4, −1.8]` log luminance |
| Sky appearance | `0.8` | Sky brightness, clipping, blue dominance, warmth, stars, noise |
| Ambient light sensor | `0.25` | Ignored when `abs(luxToLogLum(lux) − logLum) > 3` |
| Sun position prior | `0.6` with location, `0.2` without | Offline solar elevation |
| Sensor noise / ISO | `0.4` | High ISO and noise lean night |

`PhaseHysteresis` only switches phase when the candidate leads the current phase by **0.15** probability for a sustained **3 s**, extended to **5 s** for any transition involving daylight. This kills flicker at dusk without making the app sluggish.

The sun model is a NOAA-style solar position calculation using Julian date, equation of time, solar declination and hour angle. True solar time is computed as `UTC minutes + equation of time + 4 × longitude`; the time zone offset is deliberately **not** added, because doing so double-counts it. The zone is used only to synthesise a longitude when no location is available.

### Cloud model

Daytime cover is a weighted blend of greyness, brightness, texture and desaturation:

```
cover = 0.65 · grey' · (0.55 + 0.45 · bright) + 0.25 · texture + 0.10 · desaturated
```

`grey'` is greyness multiplied by a warm-sunset correction, so a saturated orange sunset is not scored as overcast. Confidence is `0.35 + 0.45 · maskConfidence + 0.20 · [fSky > 0.20]`, capped at `0.92`.

Night cover is star-based: ≥25 stars → 0.05 cover at 0.75 confidence, ≥8 → 0.35 at 0.60, ≥2 → 0.60 at 0.40. With no stars the estimate falls back to glow-versus-texture with confidence **0.18**, which is the honest part: no stars is not evidence of cloud. Day and night models are blended by night weight, with twilight counting as half night.

Effective cover shrinks toward a neutral prior when confidence is low:

```
cover_eff = c · cover + (1 − c) · 0.25
```

Classes: clear `< 0.20`, partly `< 0.60`, overcast `≤ 0.85`, heavy above that.

### Sky score

None of the four sub-scores is measured directly; each is derived from features, exposure metadata or phase probabilities.

```
brightness = Σ p(phase) · anchor(phase)                 // 0 .. 1
kappa      = 0.25·p(sunset) + 0.35·p(twilight) + 0.50·p(night)
cloud      = 1 − kappa · cover_eff                       // 0 .. 1
visibility = smoothstep(0.03, 0.30, fSky) · (0.7 + 0.3·maskConf) · tiltGate
lighting   = max(0.70, 1 − 0.30 · lightPollution)

score = round(100 · brightness · cloud · visibility · lighting)
```

The product is intentional: one bad factor is enough to ruin the result, and there is no way for a great cloud score to compensate for pointing the phone at a wall. The `0.70` floor on lighting keeps streetlights as a penalty, not a veto. `tiltGate = smoothstep(−35°, −5°, cameraElevation)` is what stops a phone aimed at the ground from reporting open sky.

SkyScore bands: `≥90 Excellent`, `≥70 Great`, `≥50 Good`, `≥30 Fair`, `≥1 Limited`, else `Unavailable`.

### Gates and message policy

Gate precedence, in order:

1. `CAMERA_BLOCKED` — either a digital-black frame (mean luminance `< 0.10`, texture `< 0.035`, noise `< 0.004`, zero stars) or a dark frame (`mean luminance < 0.08`, zero stars) while the ambient light sensor reports more than `50 lx`. A finger over the lens, or a phone face-down on a lit desk. Non-finite features also fail closed here.
2. `NO_SKY` — visibility `< 0.10`. Also reported as indoors when mean luminance is above `0.08`, which distinguishes "you are inside" from "you are pointing at the horizon".
3. `BLOCKED_DAYLIGHT` — darkness `< 0.08` and mode is not Creative.
4. `OPEN` — everything else.

The camera-blocked rule needs texture, noise and star evidence because luminance alone cannot separate a covered lens from a genuinely dark, live night sky. The texture and noise thresholds are deliberately strict: a live overcast night has sensor noise and low cloud structure, whereas a covered lens is flat and temporally dead. A perfectly smooth, noise-free dark overcast is still ambiguous, and the app errs toward not drawing.

When the gate is closed the reported score is 0 and auto rendering is disabled, but the sub-scores are still shown so the panel can explain what is wrong. Message selection is a pure function (`MessagePolicy`) with tests: camera covered, indoors, low sky, daylight, sunset, twilight, cloudy, partly clear, and a clear-night case. In Creative daylight the message is suppressed rather than claiming the sky is dark.

### Temporal behaviour

| Quantity | Behaviour |
|---|---|
| Phase | hysteresis lead 0.15, 3 s hold, 5 s around daylight |
| Luminance | EMA, `τ = 0.6 s`, `τ = 0.25 s` on jumps over 1.5 log units |
| Score | asymmetric; rises with `τ = 2.5 s`, falls with `τ = 0.8 s` |
| Render params | EMA with `τ = 1.2 s` |
| Light pollution | 7-sample median |
| AE transients | 400 ms hold after an exposure change over 0.3 EV |

The asymmetry is deliberate: the score climbs slowly so a passing cloud does not make the aurora pulse, and drops fast so daylight or a covered lens is reflected immediately.

### Adaptive rendering

`IntensityMapper` converts the smoothed score and current conditions into shader uniforms. User sliders multiply these values; they never bypass the safety gates.

```
I          = clamp(score/100 · maxIntensity, 0, 1)
brightness = userIntensity · I^1.2
saturation = lerp(0.55, 1.00, smoothstep(0.1, 0.9, I)) · userSaturation
density    = lerp(0.25, 1.00, smoothstep(0.1, 0.9, I))
glow       = lerp(0.35, 1.00, I) · (1 − 0.25 · cover)
rayContrast= lerp(0.40, 1.00, I)
speed      = lerp(0.70, 1.00, I)
sharpness  = lerp(0.50, 1.00, 1 − cover)
stars      = (1 − cover) · smoothstep(0.5, 1.0, darkness)
nightGrade = (1 − darkness) · 0.9          // Creative dusk relight
```

The `I^1.2` curve is a gentle toe so low scores look faint instead of muddy. Below `I = 0.01` the renderer is disabled outright rather than drawing a zero-alpha frame.

Mode mapping:

| Mode | Intensity index |
|---|---|
| Off | 0, renderer idle |
| Auto | `score/100` when the gate is open, otherwise 0 |
| Creative | 0 when the gate is `NO_SKY` or `CAMERA_BLOCKED`; otherwise 1.0 if locked, else `max(score/100, 0.60)` |

Creative also substitutes `cover = 0` when the user turns off cloud respect, and passes a `creative` flag so the shader applies the dusk grade and the UI can show the simulated badge.

### Renderer

`AuroraGlView` is a `GLSurfaceView` with a GLES2 context, a translucent 8/8/8/8 config and `preserveEGLContextOnPause`. It renders continuously only while the aurora is enabled, and switches to `RENDERMODE_WHEN_DIRTY` when it is idle, so a disabled overlay does not burn a frame every vsync. Rendering is paused and resumed with the activity lifecycle. The surface is fixed at **66% of the view size**, so the compositor upscales it; the aurora is a soft, low-frequency effect and does not need full resolution.

The fragment shader draws layered vertical curtains modulated by value noise, with a vertical falloff, a glow term, ray contrast, speed and a per-pixel sky-luminance gate driven by `skyLumNorm`. Stars are hash-based points whose alpha is multiplied by the star uniform. The whole thing is one full-screen triangle: no geometry, no textures, no post-processing chain.

### Performance

- Analysis target resolution 640×480, YUV_420_888, `STRATEGY_KEEP_ONLY_LATEST`, so a slow frame is dropped instead of queued.
- Feature extraction works on a 160×90 grid — 14,400 cells — and only touches sky-masked cells for colour statistics. The working `FloatArray`s are preallocated and reused; the per-frame allocation is limited to the feature snapshot and two small 8-element arrays.
- Engine work is O(grid) per frame at 5 Hz (200 ms); solar position is refreshed every 30 s and location every 10 min rather than per frame.
- GL surface at 2/3 resolution.
- Everything is offline; there are no wake locks and no background services.

### Permissions

| Permission | Why |
|---|---|
| `android.permission.CAMERA` | Required. Without it the app shows a permission screen with a shortcut to system settings. |
| `android.permission.ACCESS_COARSE_LOCATION` | Optional. Refines the offline solar position. |

There is no `INTERNET` permission. This is verified against the built APK with `aapt2 dump permissions`, and the CI artifact is the same build.

### Calibration recorder

The recorder is the answer to "these thresholds are guesses". While recording, it captures one labelled sample per second containing the feature vector, the current phase probabilities, the sub-scores, exposure data, lux, and the user-supplied labels (phase, okta 0–8, environment: rural / town / city). The buffer is capped at 3,600 samples (about an hour at 1 Hz), so a forgotten recording cannot grow memory or the exported file without bound. Export writes JSON into `files/calibration`; Share hands it to any app the user chooses. Export runs on `Dispatchers.IO` so the UI thread never touches the file. The recorder is `@Synchronized` and its published fields are `@Volatile`, because sampling happens on the analysis thread while the UI reads counts; label values are copied once per sample so a chip tap cannot split a sample across two labels.

No labelled field dataset ships with the repo, and no thresholds have been fitted yet.

### Addendum coverage

| Section | Status |
|---|---|
| §4 brightness fusion, AE handling | Implemented (`BrightnessEstimator`, AE hold in `SkyScoreEngine`) |
| §5 phase hysteresis and darkness anchors | Implemented, tested |
| §6 cloud model | Classical features only; no learned cloud head, no per-pixel density map |
| §6.5 world-fixed sky map | Not implemented |
| §7 visibility | Implemented |
| §8 lighting | Implemented (glow, blobs, foreground, sensor, median filter) |
| §9 SkyScore formula | Implemented; the §9.3 table is covered by JVM tests |
| §10 adaptive rendering | Implemented as shader uniforms; the per-pixel sky gate uses a global sky-luminance value with a vertical falloff |
| §11 temporal dynamics | Implemented (EMA, asymmetric score, 1.2 s render smoothing) |
| §12 modes, messages, UI | Implemented, English and Hindi |
| §13 code structure | `com.auroraar.skyscore.*` mirrors the spec |
| §14 performance | 5 Hz analysis, 160×90 readback, 2/3-resolution GL surface, idle render loop stopped |
| §15 calibration and tests | Recorder plus JVM tests; no labelled field data yet |
| §17 honest limits | Documented above and in the UI copy |

### Deviations from the unavailable documents

- The v3 semantic mask is replaced by a tilt-aware classical mask. It combines a vertical prior with `SkyMaskColor`, a colour plausibility term that keeps blue sky, overcast grey, deep twilight and warm sunset, while rejecting green-dominant foliage and flat neutral dark frames (a covered lens lifted by auto-exposure). The mask is eroded before statistics are taken, so edges of buildings and branches do not leak into the sky sample.
- The v2 aurora renderer is replaced by the single-pass procedural shader described above. It has no volumetric raymarching, no depth occlusion, and no world-fixed sky map.

Consequences, stated plainly: dark silhouettes above the horizon can still be counted as sky, and the aurora is a stylised effect rather than a physically based one.

### Code layout

```
app/src/main/java/com/auroraar/
├── skyscore/       engine, models, policies, estimators, features (pure Kotlin)
├── camera/         CameraX session, feature extraction, exposure/lux/pose/location readers
├── render/         procedural aurora GL view and shader
├── calibration/    JSON sample recorder
├── data/           settings persistence
└── ui/             Compose screens, sheets, theme, labels
```

### Test coverage

`./gradlew testDebugUnitTest` currently runs 82 tests across 14 JUnit test classes:

- the §9.3 intensity table rows and the 92/100 illustrative example,
- monotonicity in cloud cover and darkness,
- gate precedence (camera blocked, no-sky, daylight, Creative bypass),
- phase fusion across day, sunset, twilight, night, and the dark-frame override,
- hysteresis timing and no oscillation on a noisy signal,
- intensity mapping (daylight off, stars fade under cloud, maximum-intensity cap),
- message policy for camera covered, indoors, low sky, daylight, sunset, twilight, cloudy, partly clear, clear night, and "Creative daylight never claims the sky is dark",
- sky-mask colour plausibility (blue, overcast grey, warm sunset, deep twilight kept; green foliage, dim foliage, black frames, lifted neutral frames and dark red fabric rejected),
- solar position at Jaipur noon and midnight, plus invalid-coordinate fallback,
- end-to-end engine behaviour: daylight blocked (with and without exposure metadata), dark clear night converging to ≥ 75, a closed gate publishing score 0 immediately, Off mode disabling rendering,
- adversarial robustness: limited-range YUV expansion, plateau and NaN star rejection, non-finite sliders and probabilities, camera-blocked detection (black frame vs. dark overcast vs. live night sky vs. lit desk), Creative never drawing with no sky, and settings sanitization.

### Build and CI

Committed Gradle wrapper (`8.9`), AGP with `compileSdk 35`, `targetSdk 35`, `minSdk 26`, build-tools `35.0.0`, JDK 21 in CI.

```powershell
$env:GRADLE_USER_HOME = "$PWD\.toolchain\gradle-home"
$env:ANDROID_HOME     = "$PWD\.toolchain\android-sdk"
.\gradlew.bat testDebugUnitTest assembleDebug
```

`.github/workflows/android.yml` runs the unit tests, assembles the debug APK and uploads `aurora-ar-debug-apk` plus a JUnit report. Local toolchain, Gradle home and SDK all live under `.toolchain/` in this folder, which is gitignored; `DEPENDENCIES.md` lists every installed dependency with its uninstall command.

The app has not been run on physical hardware yet. Camera behaviour, GL compositing, sensor fusion and the Compose UI are compile-verified and logic-tested only. That is the largest open risk in this repository.

### License

TBD.
