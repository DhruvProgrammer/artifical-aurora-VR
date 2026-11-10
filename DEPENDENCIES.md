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
| `androidx.camera:camera-core` | `1.4.1` | `ImageAnalysis`, `Preview` |
| `androidx.camera:camera-camera2` | `1.4.1` | Camera2 backend + `Camera2Interop` for `CaptureResult` |
| `androidx.camera:camera-lifecycle` | `1.4.1` | binds use cases to the lifecycle |
| `androidx.camera:camera-view` | `1.4.1` | `PreviewView` |

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
- `android.permission.ACCESS_COARSE_LOCATION` — **optional**, refines the offline sun position. Default off.

There is deliberately **no `INTERNET` permission**.
