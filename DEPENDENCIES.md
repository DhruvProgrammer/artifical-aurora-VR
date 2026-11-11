# Dependencies

Everything this project needs is installed **inside this folder**, under `.toolchain/`
(gitignored). Nothing was installed system-wide. Delete `.toolchain/` and the project
is back to a plain source checkout.

```
.toolchain/
├── android-sdk/           # ANDROID_HOME
├── gradle-8.9/            # Gradle distribution
└── gradle-home/           # GRADLE_USER_HOME (all dependency + build caches)
```

Environment used for local builds:

```powershell
$env:GRADLE_USER_HOME = "$PWD\.toolchain\gradle-home"
$env:ANDROID_HOME     = "$PWD\.toolchain\android-sdk"
.\gradlew.bat assembleDebug
```

`local.properties` points at `.toolchain/android-sdk` (also gitignored).

---

## 1. Toolchain installed by me

| Dependency | Version | Why | Uninstall |
|---|---|---|---|
| Android SDK Command-line Tools | 12.0 (build `11076708`) | provides `sdkmanager`, `avdmanager` | `Remove-Item -Recurse -Force .\.toolchain\android-sdk\cmdline-tools` |
| Android SDK Platform | `android-35` (rev 2) | `compileSdk = 35` | `.\.toolchain\android-sdk\cmdline-tools\latest\bin\sdkmanager.bat --sdk_root=.toolchain\android-sdk --uninstall "platforms;android-35"` |
| Android SDK Build-Tools | `35.0.0` | aapt2 / d8 / zipalign / apksigner | `... --uninstall "build-tools;35.0.0"` |
| Android SDK Platform-Tools | `37.0.1` | `adb` for sideloading | `... --uninstall "platform-tools"` |
| Gradle | `8.9` | build system, matches AGP 8.7.3 | `Remove-Item -Recurse -Force .\.toolchain\gradle-8.9` |
| Gradle dependency cache | — | Maven artifacts, build cache, wrapper dists | `Remove-Item -Recurse -Force .\.toolchain\gradle-home` |

Nuke everything:

```powershell
Remove-Item -Recurse -Force .\.toolchain
```

**Not installed by me:** JDK 21 (Temurin) was already on this machine and is required by AGP 8.x.

---

## 2. Gradle dependencies declared in `app/build.gradle.kts`

Uninstall = delete the line, then `.\gradlew.bat --refresh-dependencies build`.

### Compose UI

| Coordinate | Version | Purpose |
|---|---|---|
| `androidx.compose:compose-bom` | `2024.10.01` | aligns every Compose artifact |
| `androidx.compose.ui:ui` | via BOM | Compose runtime + layout |
| `androidx.compose.ui:ui-graphics` | via BOM | Canvas / ring gauge |
| `androidx.compose.ui:ui-tooling-preview` | via BOM | `@Preview` annotations |
| `androidx.compose.animation:animation` | via BOM | `AnimatedVisibility`, `animateFloatAsState` |
| `androidx.compose.material3:material3` | via BOM | Material 3 components |
| `androidx.compose.material:material-icons-core` | via BOM | Settings / Close / Info / Share icons |
| `androidx.compose.ui:ui-tooling` | via BOM | `debugImplementation`, layout inspector |
| `androidx.compose.ui:ui-test-manifest` | via BOM | `debugImplementation` |
| `androidx.compose.ui:ui-test-junit4` | via BOM | `androidTestImplementation` |

### AndroidX core

| Coordinate | Version | Purpose |
|---|---|---|
| `androidx.core:core-ktx` | `1.13.1` | `ContextCompat`, `WindowCompat`, `FileProvider` |
| `androidx.activity:activity-compose` | `1.9.3` | `ComponentActivity.setContent`, permission launcher |
| `androidx.lifecycle:lifecycle-runtime-ktx` | `2.8.6` | lifecycle scope |
| `androidx.lifecycle:lifecycle-runtime-compose` | `2.8.6` | `collectAsStateWithLifecycle`, `LocalLifecycleOwner` |

### CameraX

| Coordinate | Version | Purpose |
|---|---|---|
| `androidx.camera:camera-core` | `1.4.1` | `ImageAnalysis`, `Preview`, `CameraEffect`, `SurfaceProcessor` |
| `androidx.camera:camera-camera2` | `1.4.1` | Camera2 backend + `Camera2Interop` for `CaptureResult` |
| `androidx.camera:camera-lifecycle` | `1.4.1` | binds use cases to the lifecycle |
| `androidx.camera:camera-view` | `1.4.1` | `PreviewView` |
| `androidx.camera:camera-video` | `1.4.1` | `VideoCapture`, `Recorder`, audio, MP4 muxing, `MediaStoreOutputOptions` |

`camera-video` is the only dependency added for the recording module. Uninstall it with:

```powershell
# delete the camera-video implementation line in app/build.gradle.kts, then
.\gradlew.bat --refresh-dependencies build
```

The cached artifact lives under `.toolchain/gradle-home`; remove that whole folder to reclaim the space (see §1).

### Async / tests

| Coordinate | Version | Purpose |
|---|---|---|
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | `1.9.0` | `StateFlow` on the analysis thread |
| `junit:junit` | `4.13.2` | JVM unit tests for the §9.3 table |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | `1.9.0` | test dispatchers (available for future tests) |

### Build plugins (resolved by Gradle, not vendored)

| Plugin | Version | Purpose |
|---|---|---|
| `com.android.application` | `8.7.3` | Android build |
| `org.jetbrains.kotlin.android` | `2.0.21` | Kotlin |
| `org.jetbrains.kotlin.plugin.compose` | `2.0.21` | Compose compiler |

---

## 3. Runtime permissions

No third-party SDKs, no analytics, no network stack. The manifest asks only for:

- `android.permission.CAMERA` — read the sky.
- `android.permission.RECORD_AUDIO` — **optional**, audio on recorded clips. Requested only when recording with sound enabled; denial records silent video.
- `android.permission.ACCESS_COARSE_LOCATION` — **optional**, refines the offline sun position. Default off.

There is deliberately **no `INTERNET` permission**, and no `WRITE_EXTERNAL_STORAGE`: API 26–28 recordings use the app-specific external files folder plus `MediaScannerConnection`, and API 29+ uses MediaStore. Verify the built APK with:

```powershell
& .\.toolchain\android-sdk\build-tools\35.0.0\aapt2.exe dump permissions app\build\outputs\apk\debug\app-debug.apk
```

(`aapt2.exe` is under `.toolchain/android-sdk/build-tools/35.0.0/`.)

---

## 4. Optional offline tooling (not installed)

`tools/alignment_check.py` is an optional measurement harness. It is **not** part
of the app build and was **not installed** in this workspace. If you choose to
run it, keep it inside this folder:

```powershell
python -m venv .toolchain\venv
.\.toolchain\venv\Scripts\python.exe -m pip install opencv-python numpy
.\.toolchain\venv\Scripts\python.exe tools\alignment_check.py clip.mp4
```

Uninstall:

```powershell
Remove-Item -Recurse -Force .\.toolchain\venv
```

The pip cache defaults to the user profile; to keep it inside this folder:

```powershell
$env:PIP_CACHE_DIR = "$PWD\.toolchain\pip-cache"
```

The app itself has no Python, OpenCV or NumPy dependency.

---

## 5. Editor/agent skills (not part of the app build)

These are markdown instruction files for the coding agent, not app dependencies.
They do **not** live in this folder: OpenCode only discovers skills from its own
configuration directory, so they are installed under `~/.config/opencode/skills/`.
Nothing is compiled, no package manager is involved, and removing them cannot
affect the Android build.

| Skill | Location | Why | Uninstall |
|---|---|---|---|
| `bug-free-workflow` | `%USERPROFILE%\.config\opencode\skills\bug-free-workflow\SKILL.md` | Verification-first workflow: test-first changes, mandatory build/test/lint proof before claiming done, adversarial review passes | `Remove-Item -Recurse -Force "$env:USERPROFILE\.config\opencode\skills\bug-free-workflow"` |

To confirm it is installed:

```powershell
Get-ChildItem "$env:USERPROFILE\.config\opencode\skills"
```

Skills are read at startup; restart the session after adding or removing one.

---

## 6. Release signing keystore (not a dependency, but a required secret)

The release APK is signed with a self-signed 4096-bit RSA key. It lives **inside
this folder** under `.toolchain/keystore/`, which `.gitignore` already excludes,
so it is never committed and CI never sees it.

| File | Purpose |
|---|---|
| `.toolchain/keystore/aurora-release.jks` | The private key. **Back this up.** |
| `.toolchain/keystore/keystore.properties` | Store/key passwords and alias read by Gradle. |

`app/build.gradle.kts` loads that properties file only if it exists. When it is
absent (CI, fresh clone) no `release` signing config is created and
`assembleDebug` / `testDebugUnitTest` still work; only `assembleRelease` would
produce an unsigned APK. This was verified by hiding the folder and running a
debug build.

Certificate: `CN=Aurora AR, OU=Mobile, O=Aurora AR, L=Delhi, ST=Delhi, C=IN`,
SHA-256 `4c85a6d93b1e943ac38b2b0a06161f9dd6ac1d3472cd4c384cadce49f9afa018`.

Verify a build:

```powershell
.\.toolchain\android-sdk\build-tools\35.0.0\apksigner.bat verify --print-certs app\build\outputs\apk\release\app-release.apk
```

Uninstall / revoke:

```powershell
Remove-Item -Recurse -Force .\.toolchain\keystore
```

**Keep a copy of `aurora-release.jks` somewhere safe.** Android identifies an app
by its signing key: if it is lost, the same `com.auroraar` package can never be
updated in place again and every user must uninstall first. Deleting the folder
only stops *new* signed releases; it cannot invalidate already-published ones.
