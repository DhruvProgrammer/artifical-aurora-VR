# Aurora AR — SkyScore and recording

An Android app that paints a fake aurora over your live camera picture. It watches the sky through the camera and changes the aurora to match: brighter on a dark clear night, faint at dusk, gone in daylight. It can record the final composite — real camera plus aurora — to an MP4 with optional sound, pause and resume, and a post-record preview you can share.

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
- A large **record button** with a Free progress ring, timer, pause/resume, camera flip, a **photo shutter**, **Customize** and **Gallery** pills, a large **aurora orb** (tap to toggle, hold for modes) and a vertical intensity rail.
- **Ten aurora styles** (three Free, seven Premium) plus Premium cinematic looks, layer/coverage/wave/ray/burst controls, direction, and intensity automation. Locked styles show a 3-second live preview before the paywall.
- **Hold-to-record** as an alternative to tap-to-record, and **keep-screen-on** while the camera is open.
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

### Recording and sharing

- The red button records exactly what the screen shows: the live camera with the aurora composited on top. The app's buttons and text are **not** in the file.
- While recording there is a timer, a progress ring for the Free limit, and a pause/resume button. Paused time does not count toward the limit and is cut from the recording.
- Stop the clip and a preview dialog opens with looped playback, **Share**, **Delete** and **Record again**. Sharing uses Android's normal share sheet; nothing is uploaded by the app.
- **Photos:** the shutter saves the composited camera + aurora frame to **Pictures/AuroraAR**. Still photos carry no visible watermark on either plan; the simulated-origin disclosure is written as metadata.
- **Gallery:** an in-app sheet lists this app's clips with thumbnails and duration/size, and offers playback, generic share, quick share to installed WhatsApp/Instagram/YouTube apps, and delete with confirmation.
- **Hold-to-record:** when enabled in Settings, press and hold the record button to record, release to stop. The 3-2-1 countdown remains available for tap-to-record.
- **Interruptions:** when audio focus is lost (for example an incoming call), the take pauses instead of recording over the call. Storage, battery and thermal checks still stop a take safely.
- **Orphan cleanup:** on launch, stale `IS_PENDING` MediaStore rows and legacy zero-byte/temp files left by process death are removed.
- **Free:** 720p, up to **10 seconds**, small **Aurora AR — SIMULATED** watermark in the bottom-right corner. The last three seconds show a large countdown and tick with haptics.
- **Premium:** 1080p, no time limit, no watermark, every style and advanced control. Billing is **not wired in this build**. Release builds are always Free; debug builds expose a local switch so both policies can be tried.

Files go to **Movies/AuroraAR** through MediaStore on Android 10 and newer. On Android 8–9 they are written to the app's own external files folder and indexed so a gallery can find them.

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
- Camera permission. Microphone permission is optional and only requested when you start a recording with sound enabled; if you refuse, clips are silent. Location permission is optional: if you allow it, the app can place the sun more accurately for the time of day. If you refuse, it falls back to your time zone and still works.

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
- asks for the microphone only when you record with sound enabled; denial produces a silent clip,
- writes videos to `Movies/AuroraAR` (Android 10+) or the app's external files folder (Android 8–9),
- writes calibration recordings only to the app's own storage, and only shares them if you press Share,
- shares a video only when you press Share, using the system chooser; the app itself never sends it anywhere,
- has Android backup and device-transfer extraction disabled, so its settings and recordings are not copied off the phone by the system either.

---

## Part 2 — Technical reference

Everything below this line is implementation detail. It assumes you know Android and some signal processing.

### Project provenance

This repo implements the **SkyScore and Adaptive Intensity Engine** from an addendum labelled v4. The companion v2 and v3 documents (the full AR aurora renderer and the semantic sky-mask/occlusion engine) were not available, so two parts are substitutes, not ports:

- **Renderer** — a compact procedural GLSL shader instead of the v2 aurora renderer.
- **Sky mask** — a classical tilt-and-colour mask instead of v3's semantic segmentation and ARCore depth occlusion.

It also implements the **v5 recording/share module**. The prompt specified a hand-written `MediaCodec`/`MediaMuxer` pipeline with a shared EGL context, its own `TimeBase`, and CFR timestamps. This build instead uses CameraX `VideoCapture` + `Recorder` for encoding, muxing, audio, timestamps and MediaStore, and a CameraX `SurfaceProcessor` for the GL composite. That substitution is deliberate and its limits are listed under [Recording pipeline](#recording-pipeline). Both are flagged in code and in the coverage tables below.

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
  ├─ Compose UI (chip, sheet, settings, calibration, recording chrome)
  └─ CompositeProcessor (CameraX SurfaceProcessor, GLES2)
       ├─ camera OES texture → aurora shader → Free watermark
       └─ CameraX fan-out → Preview + VideoCapture encoder
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

### Renderer and compositor

There is no separate overlay view any more. `CompositeProcessor` is a CameraX `SurfaceProcessor` with one EGL14 context on a dedicated `HandlerThread`. For every camera frame it:

1. binds the camera's external OES texture and draws it full-screen with the transform CameraX supplies (`SurfaceOutput.updateTransformMatrix`), so preview and file are upright in portrait and landscape;
2. draws the same procedural aurora shader used before, with premultiplied-alpha blending and the current `AuroraRenderParams`;
3. draws the Free watermark (a pre-rasterised ARGB bitmap with the wordmark and a `SIMULATED` line) as one textured quad in the bottom-right safe area;
4. calls `eglPresentationTimeANDROID` with the camera timestamp and swaps buffers.

CameraX is bound with one effect targeting `PREVIEW | VIDEO_CAPTURE`, which maps to `OUTPUT_OPTION_ONE_FOR_ALL_TARGETS`: a single composited output is fanned out to the `PreviewView` and to the video encoder. The file therefore contains the same aurora the user sees, minus the Compose chrome, which never enters this GL pipeline. `ImageAnalysis` is a separate use case on the raw camera stream, so SkyScore never reads its own overlay.

The fragment shader draws layered vertical curtains modulated by value noise, with a vertical falloff, a glow term, ray contrast, speed and a per-pixel sky-luminance gate driven by `skyLumNorm`. Stars are hash-based points whose alpha is multiplied by the star uniform. The whole thing is two full-screen draws plus the watermark: no geometry, no textures for the aurora, and no post-processing chain. During a recording the aurora animation is driven by the controller's recorded clock (which excludes pauses) instead of raw camera time, so pausing does not make the curtains jump when the file resumes; outside a recording it uses the camera timestamp.

### Recording pipeline

`RecordingController` is the single owner of recording state. Every mutator runs on the main thread; the GL thread only reads the recorded clock. The state machine is `IDLE → PREPARING → RECORDING ⇄ PAUSED → FINALIZING → SAVED | ERROR`.

- **Start checks.** `DeviceGuards` reads free space on the recordings volume, battery percentage/charging state and thermal status. Less than **100 MB** free, less than **5%** battery while unplugged, or thermal `CRITICAL` refuses the start. Less than **500 MB**, less than **15%** while unplugged, or thermal `MODERATE` raises a non-blocking toast. A 2-second monitor re-checks while recording and stops cleanly at a hard limit, so the muxer still finalises a playable file.
- **Quality.** Free requests 720p (`Quality.HD`), Premium requests 1080p (`Quality.FHD`), with `FallbackStrategy.lowerQualityOrHigherThan` so an unsupported request degrades instead of failing. CameraX selects H.264/AVC and its default bitrate. This build does not expose 4K, HEVC, 60 fps or custom bitrates. `DeviceRecordingCaps` probes `MediaCodecList` and Settings shows the encoders the device actually reports; it is informational, not a second encoder selector.
- **Audio.** The microphone is requested only when a recording actually starts with sound enabled. `withAudioEnabled()` is called only after `RECORD_AUDIO` is granted; otherwise the clip is silent and the timer pill shows "Silent". While audio is recording the controller holds `AUDIOFOCUS_GAIN_TRANSIENT`; a transient or permanent loss pauses the take (the user can resume or stop and save) instead of recording over a call.
- **Duration.** Free's 10 s is enforced against recorded time, not wall time: only `RECORDING` segments accumulate, a 100 ms ticker updates the pill, and a scheduled limit job stops at 10 s. Paused time is excluded, and CameraX also removes paused spans from the file timeline. Stop latency means a clip can be a few hundred milliseconds over 10 s; this was not measured on hardware.
- **Pause/resume clock.** `Recording.pause()/resume()` produces `Pause`/`Resume` events and a file with no gap. While a recording is active the compositor asks `RecordingClock.recordedElapsedMs()` for the aurora time, so curtains do not jump across a pause. The clock is millisecond-accurate in the controller but frame-granular in GL, not a frame-exact PTS rewrite.
- **Watermark.** Free clips get a pre-rasterised 1024×288 ARGB texture (wordmark plus `SIMULATED`), drawn at 26% of frame width (capped at 420 px), 3.5% from the right and 14% from the bottom. The same draw is visible in the viewfinder while a Free recording runs, so preview and file agree. Premium draws alpha 0. Still photos are read back **before** this draw, so photos never carry the visible watermark on either plan.
- **Styles and grade.** `AuroraStyleRegistry` supplies ten data-driven styles. Free has Nordic Classic, Arctic Violet and Rose Quartz; Premium adds Solar Storm, Emerald Veil, Desert Dream, Corona Crown, Crimson Arc, Pillars of Light and Festival. Geometry (curtain, corona, arc, pillars, festival), palette, layer count, coverage, wave amplitude/frequency, burst and direction are shader uniforms, so these are real shape variants rather than recolours. Premium cinematic looks (Cinematic, Dreamy) and grain are applied in the camera pass of the shared composite, which keeps the preview and the encoded file identical. `FeatureGate` is the single authority: Free renders its selected style's base values and no premium grade/automation, while the persisted values remain for a later purchase.
- **Orientation.** `targetRotation` is fixed when the camera binds. Rotation changes are ignored while a recording is active, and the manifest handles orientation/screen-size config changes without recreating the activity, so the file keeps the orientation it started with. Portrait and landscape are both supported.
- **Hold-to-record.** With the setting enabled, pointer-down starts a take and pointer-up stops it. If the release arrives before CameraX emits `Start`, the controller is flagged to stop as soon as the take becomes active. Permission dialogs cannot be awaited inside a gesture, so hold-to-record starts silent when the mic was never granted. The camera screen also holds `FLAG_KEEP_SCREEN_ON` so a long take does not sleep the display.
- **Output.** API 29+ uses `MediaStoreOutputOptions` with `RELATIVE_PATH=Movies/AuroraAR`, MIME `video/mp4`, and `TITLE`/`DESCRIPTION` set to "Simulated aurora – made with Aurora AR". API 26–28 writes into the app's external `Movies/AuroraAR` folder and runs `MediaScannerConnection`, so the clip appears in the gallery without `WRITE_EXTERNAL_STORAGE`. Photos follow the same split into `Pictures/AuroraAR` (`image/jpeg`, quality 92).
- **Photos.** `CompositeProcessor.requestPhoto()` grabs the next fully composited frame with `glReadPixels` before the watermark draw, flips the rows, builds an ARGB bitmap and hands it to `PhotoSaver` on `Dispatchers.IO`. A 3-second watchdog reports an error if the camera never produces a frame. Photos are blocked during a video take.
- **Gallery.** `RecordingsRepository` lists only this app's output through MediaStore `RELATIVE_PATH` (API 29+) or the app-specific folder (26–28), so no storage permission is required. Thumbnails are decoded with `MediaMetadataRetriever` off the main thread. Quick-share targets are filtered against `PackageManager`, and the manifest declares `<queries>` for WhatsApp, Instagram and YouTube so visibility works on Android 11+.
- **Orphan cleanup.** On launch `PendingRecordingCleanup` deletes `IS_PENDING` MediaStore rows owned by this package and older than 60 seconds, plus legacy `.tmp`/zero-byte files in `Movies/AuroraAR` older than the same grace period. Recent rows are never touched, so another in-flight take cannot be deleted.
- **Post-record.** A `VideoView` dialog loops the result and offers Share, Delete and Record again. Share uses `ACTION_SEND` on the MediaStore or `FileProvider` URI with the `(simulated)` caption; the dialog shows the AI-label tip. Delete removes the MediaStore row or the legacy file.
- **Preview vs file.** One CameraX effect targets both `PREVIEW` and `VIDEO_CAPTURE`, so CameraX fans a single composited output out to both sinks. The encoder receives exactly the previewed aurora; Compose chrome never enters the effect. The trade-off is that preview and encoder share one resolution, plus an extra GPU copy.
- **Fallback chain.** If Preview + Video + effect fails, the app retries Preview + effect (live preview, recording hidden); if that also fails it binds plain Preview + ImageAnalysis and says the effect is unavailable. An effect failure during a take stops and finalises the recording.

### Performance

- Analysis target resolution 640×480, YUV_420_888, `STRATEGY_KEEP_ONLY_LATEST`, so a slow frame is dropped instead of queued.
- Feature extraction works on a 160×90 grid — 14,400 cells — and only touches sky-masked cells for colour statistics. The working `FloatArray`s are preallocated and reused; the per-frame allocation is limited to the feature snapshot and two small 8-element arrays.
- Engine work is O(grid) per frame at 5 Hz (200 ms); solar position is refreshed every 30 s and location every 10 min rather than per frame.
- The GL composite renders at the encoder size (720p Free, 1080p Premium) with one OES texture, two full-screen quads and one watermark quad. The aurora shader now evaluates up to four layers, so fragment cost rises with the style's layer count; Premium styles with four layers are the worst case. Preview and encoder share that output.
- Still capture is on demand: one `glReadPixels` of the composite (about 3.7 MB at 720p, 8.3 MB at 1080p), a row flip into an `IntArray`, then JPEG compression on `Dispatchers.IO`. It can cause a single-frame hitch and is not part of steady-state recording cost.
- The capability probe runs once at screen creation and reads `MediaCodecList` metadata; the gallery decodes thumbnails only when opened.
- Everything is offline. There are no wake locks and no background services; the camera screen only adds `FLAG_KEEP_SCREEN_ON` while it is visible.

### Permissions

| Permission | Why |
|---|---|
| `android.permission.CAMERA` | Required. Without it the app shows a permission screen with a shortcut to system settings. |
| `android.permission.RECORD_AUDIO` | Optional. Requested only when a recording starts with sound enabled; denial records silent video. |
| `android.permission.ACCESS_COARSE_LOCATION` | Optional. Refines the offline solar position. |

There is no `INTERNET` permission and no `WRITE_EXTERNAL_STORAGE`. This is verified against the built APK with `aapt2 dump permissions`, and the CI artifact is the same build.

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
| §14 performance | 5 Hz analysis, 160×90 readback, encoder-sized GL composite (720p Free / 1080p Premium), idle render loop stopped |
| §15 calibration and tests | Recorder plus JVM tests; no labelled field data yet |
| §17 honest limits | Documented above and in the UI copy |

### Recording module coverage (v5 prompt)

| Requirement | Status |
|---|---|
| Final composited file (camera + aurora + watermark), no app UI | Implemented with a CameraX `SurfaceProcessor`; one output feeds preview and encoder |
| Aurora world-locked to camera pose, occluded by foreground | Not implemented; the aurora is screen-space and the sky mask only gates drawing. No depth/segmentation occlusion. The required alignment test therefore has no world grid to measure |
| Optional audio | Implemented through CameraX `Recorder`; permission requested only at start |
| Start / pause / resume / stop, timer excluding pauses | Implemented in `RecordingController` |
| Free 10 s hard limit | Implemented against recorded time; stop latency not hardware-measured |
| Free 720p / Premium 1080p | Implemented with `Quality.HD` / `Quality.FHD` and a lower-quality fallback |
| Free watermark, Premium none | Implemented in the encoder pass and shown in the viewfinder while recording |
| Portrait and landscape | Implemented via `targetRotation`; rotation changes are ignored mid-take |
| Camera flip, disabled while recording | Implemented (rear ⇄ front when the device has both) |
| Save to gallery + invisible disclosure | Implemented: MediaStore on API 29+, app-specific file + MediaScanner on 26–28, `TITLE`/`DESCRIPTION` disclosure |
| Post-record preview, share, delete, record again | Implemented; system share sheet. Gallery adds a per-app quick-share row for installed WhatsApp/Instagram/YouTube |
| Free/Premium entitlement model | Implemented locally; debug-only switch, release always Free |
| Play Billing, restore, offline grace | Not implemented — requires `INTERNET` and a Play Console account |
| Photo capture and photo watermark rules | Implemented: GL readback of the next composite, saved to `Pictures/AuroraAR`, no visible watermark on either plan, disclosure metadata |
| In-app gallery screen | Implemented: thumbnails, playback, quick share, delete with confirmation |
| 4K, HEVC, 60 fps, custom bitrates | Not exposed; CameraX `Recorder` owns codec/frame-rate selection. `DeviceRecordingCaps` reports what the hardware can do |
| Premium auto-split at ~3.5 GB / 30 min | Deferred; CameraX writes one file |
| Hold-to-record and cinematic looks | Implemented: hold gesture plus Natural/Cinematic/Dreamy grades and grain |
| Advanced/premium aurora styles and controls | Implemented: 3 Free + 7 Premium styles, four geometries, layers, coverage, waves, speed, ray sharpness, burst, direction, automation |
| Mid-recording call / audio-focus / mic-revocation handling | Audio focus pauses the take; mic revocation mid-take is not handled; storage, battery and thermal stops are handled |
| Process-death recovery of orphaned pending files | Implemented: stale `IS_PENDING` rows and legacy temp files are cleaned at launch |
| Device capability probe screen | Implemented in Settings (encoder list and supported sizes); informational only |
| Debug grid + optical-flow alignment test | Debug grid is not implemented, so the script in `tools/` cannot produce the required world-lock numbers |
| Measured A/V drift, duration accuracy, FPS, thermals | Not measured — no device was connected |

### Deviations from the unavailable documents

- The v3 semantic mask is replaced by a tilt-aware classical mask. It combines a vertical prior with `SkyMaskColor`, a colour plausibility term that keeps blue sky, overcast grey, deep twilight and warm sunset, while rejecting green-dominant foliage and flat neutral dark frames (a covered lens lifted by auto-exposure). The mask is eroded before statistics are taken, so edges of buildings and branches do not leak into the sky sample.
- The v2 aurora renderer is replaced by the single-pass procedural shader described above. It has no volumetric raymarching, no depth occlusion, and no world-fixed sky map.
- The v5 recording engine is replaced by CameraX `VideoCapture` + `Recorder` with a `SurfaceProcessor` for the composite, instead of a hand-written `MediaCodec`/`MediaMuxer`, shared-EGL encoder thread, `TimeBase` and CFR scheduler. CameraX owns encoding, muxing, audio timestamps, pause offsets and MediaStore finalisation; the controller owns policy, limits, guards and UI state. This removes a large class of codec/muxer bugs but also removes frame-exact control.

Consequences, stated plainly: dark silhouettes above the horizon can still be counted as sky, the aurora is a stylised effect rather than a physically based one, the Free limit can overshoot by the encoder's stop latency, the recorded frame rate and bitrate are CameraX defaults rather than tuned values, and there is no crash-resilient fragmented MP4 or automatic file split. None of the recording path has been exercised on a real camera, encoder or microphone.

### Code layout

```
app/src/main/java/com/auroraar/
├── skyscore/       engine, models, policies, estimators, features (pure Kotlin)
├── camera/         CameraX session, feature extraction, exposure/lux/pose/location readers
├── capture/        recording policy/guards, CameraX effect + GL compositor, controller, watermark
│   └── gl/         EGL wrapper, camera/aurora shaders, watermark texture
├── calibration/    JSON sample recorder
├── data/           settings persistence
└── ui/             Compose screens, sheets, theme, labels
    └── components/ chrome, score ring, settings, recording controls
```

### Test coverage

`./gradlew testDebugUnitTest` currently runs 120 tests across 20 JUnit test classes:

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
- adversarial robustness: limited-range YUV expansion, plateau and NaN star rejection, non-finite sliders and probabilities, camera-blocked detection (black frame vs. dark overcast vs. live night sky vs. lit desk), Creative never drawing with no sky, and settings sanitization,
- recording policy: Free 720p/10 s/watermark vs. Premium 1080p/unlimited/no watermark, duration formatting, watermark visibility only for active Free takes, the `active` phase set, and the HEVC/60 fps/4K flags staying off,
- safety guards: storage/battery/thermal refusals and warnings, threshold boundaries, unknown battery readings, refusal precedence, and charging exceptions,
- premium catalogue: 3 Free + 7 Premium styles, unique ids, fallback for unknown ids, five geometry variants, and shader-contract ranges for layers/coverage/waves/burst/direction,
- feature gate: both tiers, premium-style locking, unknown-style fallback, Free downgrade of grade/automation/advanced values, and preservation of Premium choices,
- style-aware intensity mapping: geometry/palette/layers/waves/burst/direction reach the uniforms, cinematic looks set the grade id and grain,
- orphan cleanup grace period: stale, recent, missing and future `dateAdded` values,
- encoder capability probe: per-codec resolution/frame-rate checks, hardware flags, empty-device fallback and human-readable summary.

### Build and CI

Committed Gradle wrapper (`8.9`), AGP with `compileSdk 35`, `targetSdk 35`, `minSdk 26`, build-tools `35.0.0`, JDK 21 in CI.

```powershell
$env:GRADLE_USER_HOME = "$PWD\.toolchain\gradle-home"
$env:ANDROID_HOME     = "$PWD\.toolchain\android-sdk"
.\gradlew.bat testDebugUnitTest assembleDebug
```

`.github/workflows/android.yml` runs the unit tests, assembles the debug APK and uploads `aurora-ar-debug-apk` plus a JUnit report. Local toolchain, Gradle home and SDK all live under `.toolchain/` in this folder, which is gitignored; `DEPENDENCIES.md` lists every installed dependency with its uninstall command.

### Verification status

Local verification on 2026-11-11:

- `testDebugUnitTest`: **120 tests, 20 classes, 0 failures**.
- `lintDebug`: **0 errors, 10 warnings** (nine "newer dependency available" notes and one obsolete `mipmap-anydpi-v26` folder note).
- `assembleDebug`: APK **12,534,367 bytes**.
- Built APK permissions: `CAMERA`, `RECORD_AUDIO`, `ACCESS_COARSE_LOCATION`; no `INTERNET`, no `WRITE_EXTERNAL_STORAGE`.

**No Android device was connected** (`adb devices` was empty). Everything below is compile-verified, lint-checked or JVM-tested only. None of the following has been observed running: CameraX effect binding, the GL compositor, the encoder, audio capture, pause/resume timestamps, orientation handling, MediaStore finalisation, the `VideoView` preview, share/delete, thermal behaviour, or the watermark's actual pixels.

Manual test matrix — record the result per device, then update this table:

| # | Test | Expected |
|---|---|---|
| 1 | Install on Android 10+, grant camera, open the screen | Live aurora preview, no crash, record enabled |
| 2 | Free take, 10 s | Ring turns amber in the last 3 s, large countdown and haptic ticks, auto-stop near 10 s, preview opens, file plays, watermark bottom-right |
| 3 | Pause 5 s, resume, stop | Timer and file duration exclude the pause; no frozen segment; aurora continues smoothly |
| 4 | Enable debug Premium, record > 10 s | 1080p, no watermark, no limit |
| 5 | Deny microphone | Silent clip; "Silent" marker; no crash |
| 6 | Portrait take, landscape take, rotate mid-take | Both clips upright; mid-take rotation does not recreate the activity or rotate the file |
| 7 | Flip camera, then start a take and try to flip | Preview flips while idle; flip is ignored while recording |
| 8 | Share, delete, record again | System chooser opens; delete removes the item; record again starts a fresh take |
| 9 | Press Home during a take, return | Clip finalises and saves; no half-written or corrupt file |
| 10 | Fill storage below 500 MB then below 100 MB | Warning toasts, then start is refused; mid-take hard limit stops and saves |
| 11 | Battery below 15%, then below 5% unplugged | Warning, then refusal / mid-take stop with a saved clip |
| 12 | Warm the device to thermal `MODERATE` / `CRITICAL` | Warning, then clean stop with a saved clip |
| 13 | Device that rejects the effect composition | Preview-only fallback with the "recording unavailable" message |
| 14 | Android 8–9 device | Legacy external file + MediaScanner visibility, FileProvider share works |
| 15 | 30-minute Premium soak at 1080p | File plays end to end; record A/V drift, dropped frames and temperature |
| 16 | Share to Google Photos, WhatsApp, Instagram Reels, YouTube | Correct colours, orientation and duration; watermark clear of platform UI |
| 17 | Incoming call / audio-focus loss | Take pauses, then resumes or stops cleanly; no call audio in the clip |
| 18 | Tap a Premium style as Free | Style previews on the viewfinder for 3 s, then the paywall opens; persisted style stays Free |
| 19 | Debug Premium: every style, look, slider, direction and automation | Preview and file match; Cinematic/Dreamy grade and grain visible; choices survive restart |
| 20 | Photo while idle, then while recording | Idle photo saved to `Pictures/AuroraAR` with no visible watermark; recording shows "stop recording" toast |
| 21 | Open Gallery | Thumbnails, duration/size, playback, share, installed quick-share targets and delete confirmation work |
| 22 | Hold-to-record: press/release, release during `PREPARING`, hold to the Free limit | Starts on press, stops on release; no runaway take; limit still stops and saves |
| 23 | Enable hold-to-record while mic is denied | Take starts silent, no permission dialog interrupts the gesture |
| 24 | Kill the process during a take, relaunch | No unplayable pending row or stale temp file; a fresh take works |
| 25 | Open Settings | Encoder list matches `MediaCodecList`; H.264/HEVC and max sizes shown, no false 4K/60 promise |
| 26 | Set a 15-second screen timeout, keep the camera open | Display does not sleep; leaving the screen releases `FLAG_KEEP_SCREEN_ON` |
| 27 | Mic revoked mid-take, process death | **Not implemented**; audio may stop or the file may end early. Orphan cleanup only handles the saved state |

There are no measured FPS, dropped-frame, A/V-drift, duration-accuracy, battery or temperature numbers. The budgets in the v5 prompt are targets, not results.

### License

TBD.
