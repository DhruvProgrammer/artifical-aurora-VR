# C: Drive Analysis — 12 Nov 2026

**Read-only analysis. Nothing was deleted, moved, or modified.**

Scope: `C:\` on Windows 10 Pro 19045, 208.25 GB NTFS volume, 8 GB RAM.
Method: `robocopy /L` directory sizing (list-only mode), per-folder drill-down, WMI queries, registry reads.
Scan script kept here for reuse: [`Scan-DirSizes.ps1`](Scan-DirSizes.ps1)

---

## 1. Current state

| Metric | Value |
|---|---|
| Capacity | 208.25 GB |
| Used | 156.97 GB (75.4%) |
| Free | **51.28 GB (24.6%)** |
| Second drive `D:` | 29.6 GB total, 4.7 GB free |

**Verdict:** C: is *not* critically full at the moment. At the start of the scan free space was 49.9 GB; the pagefile resized from 11.8 GB to 9.5 GB while scanning, so free space moves by a few GB on its own. The pressure comes from a large user profile (84 GB) and heavy application installs (27 GB), plus a probable ~10 GB of System Restore shadow copies that cannot be seen without admin rights.

---

## 2. Where the 157 GB goes

| Location | GB | Share of used |
|---|---:|---:|
| `C:\Users` | 84.03 | 53.5% |
| `C:\Program Files` | 21.08 | 13.4% |
| `C:\Windows` | 20.85 | 13.3% |
| `C:\pagefile.sys` | 9.54 | 6.1% |
| `C:\Program Files (x86)` | 5.88 | 3.7% |
| `C:\ProgramData` | 2.74 | 1.7% |
| `C:\mingw64` | 0.90 | 0.6% |
| `C:\adobeTemp` | 0.78 | 0.5% |
| `C:\MinGW` | 0.28 | 0.2% |
| `C:\swapfile.sys` + misc | 0.02 | 0.0% |
| **Unaccounted** (see §9) | **10.88** | **6.9%** |
| **Total used** | **156.97** | 100% |

---

## 3. `C:\Users\MR.PC` — 84.03 GB

| Folder | GB | Notes |
|---|---:|---|
| `AppData` | 58.57 | Local 41.85 / Roaming 16.49 / LocalLow 0.23 |
| `Desktop` | 6.76 | `bcode\aether` 3.25 (Rust `target` 3.14), `auror` 2.52 (`.toolchain` 2.34), `wp-bot` 0.70 |
| `.vscode\extensions` | 5.50 | 40+ extensions; ChatGPT 1.54 + two 0.8/0.7 GB folders |
| `.t3` | 2.47 | `userdata\statev2.sqlite` 1.65 — T3 Code's own database |
| `Documents` | 2.18 | `Image-Line` 2.17 (FL Studio data + 1.19 GB of installers) |
| `.bun` | 1.68 | Install cache 1.49 + binaries 0.18 |
| `.local` | 1.48 | Share 1.15 (Claude 0.84, OpenCode 0.31) + bins 0.32 |
| `Downloads` | 1.03 | Telegram Desktop 0.85 |
| `.rustup` + `.cargo` | 1.88 | Rust toolchain 0.94 + crate registry 0.94 |
| `Local Sites` | 0.60 | Local by Flywheel WordPress sites |
| `.cloakbrowser` | 0.52 | Browser profile |
| `.cache` | 0.42 | Misc caches |
| Other dotfolders/files | ~0.9 | `.claude`, `.virtualenvs`, `.omp`, `.opencode`, `.codex`, `.gemini`, `.config`, etc. |

---

## 4. `AppData\Local` — 41.85 GB (the single biggest stack)

| Folder | GB | What it is |
|---|---:|---|
| `npm-cache` | **7.22** | `_cacache` 6.26 + `_npx` 0.96 — pure npm download cache |
| `Programs` | 6.28 | Local 2.16, Python 1.43, VS Code 1.11, OpenCode Desktop 0.83, T3 Code 0.74 |
| `Google` | 4.88 | Chrome 4.13 (Profile 6 = 3.47) + DriveFS 0.75 |
| `ms-playwright` | 3.19 | 3 Chromium, 3 Firefox, 3 headless shells, WebKit, ffmpeg |
| `BraveSoftware` | 2.71 | Default profile 2.51 (File System 1.27, Code Cache 0.71, Cache 0.39) |
| `Microsoft` | 2.56 | Edge 0.78, Office 0.64, RDP 0.29, Windows 0.23, Copilot 0.19, cpptools 0.16 |
| `Packages` | 2.08 | Search index 0.70, Python 0.54, OfficeHub 0.42 |
| `pnpm` | 1.53 | pnpm content-addressable store |
| `WisprFlow` | 1.49 | Two full app versions (0.585 + 0.584) — one is stale |
| `hermes` | 1.26 | `hermes-agent` install |
| `Postman` | 0.87 | App data |
| updater folders | **3.45** | local-updater, MiniMax, T3 Code, Waves Central, Antigravity, OpenCode, Native Access |
| `SquirrelTemp` | 0.58 | Installer temp copies (claude.exe twice) |
| `OpenAI` | 0.55 | App data |
| `Mozilla` | 0.53 | Firefox profile |
| `uv` | 0.50 | Python package cache |
| `agy` | 0.40 | App data |
| `Java` | 0.32 | Runtime |
| `electron` | 0.26 | Electron cache |
| rest | ~1.2 | plannotator, pip, node-gyp, Temp 0.10, crash dumps, etc. |

**Chrome detail (Profile 6 = 3.47 GB):** Service Worker 1.29 + Cache 0.81 + Code Cache 0.52 + File System 0.31 + IndexedDB 0.26 + Storage 0.16. About 2.6 GB is cache-class data.

---

## 5. `AppData\Roaming` — 16.49 GB

| Folder | GB | Notes |
|---|---:|---|
| `Code` | **5.75** | `CachedExtensionVSIXs` 3.84 (installer copies) + `Crashpad` 1.11 (crash dumps) + User 0.28 |
| `npm` | 2.21 | Globally installed npm packages (`node_modules`) |
| `Local` | 1.96 | Local by Flywheel: `lightning-services` 1.59 (PHP/MySQL builds) + `run` 0.30 |
| `Adobe` | 1.61 | Adobe app data |
| `Telegram Desktop` | 1.26 | `tdata` 1.02 — message/media cache |
| `Claude` | 0.89 | Claude Desktop data |
| `Bivaji Coms` | 0.43 | App data |
| `uv` | 0.38 | Python tool cache |
| `com.adobe.dunamis` | 0.37 | Adobe |
| `ai.opencode.desktop` | 0.27 | OpenCode Desktop |
| `t3code-v2` | 0.26 | T3 Code |
| rest | ~0.5 | Postman, Lockdown, Python, Mozilla, LABS, MiniMax, etc. |

---

## 6. Installed applications

### `C:\Program Files` — 21.08 GB
| App | GB |
|---|---:|
| Adobe (After Effects 2020 3.61, Premiere Pro 2022 3.26, Photoshop 2024 0.39) | 7.33 |
| Microsoft Office | 3.00 |
| Image-Line (FL Studio) | 2.74 |
| WSL (system.vhd) | 1.14 |
| Brave | 1.06 |
| Avast | 0.85 |
| OneDrive | 0.55 |
| Google / Common Files | 0.51 / 0.51 |
| .NET, Git, Proton, Red Giant, Firefox, Conexant | ~2.3 |
| rest | ~1.1 |

### `C:\Program Files (x86)` — 5.88 GB
- Microsoft 4.63 → **Copilot 1.42 + EdgeCore 1.39 + Edge 0.88 + WebView 0.86 + EdgeUpdate 0.08**
- Google 0.54, Common Files 0.36, Jagannatha Hora 0.16

### `C:\ProgramData` — 2.74 GB
- BorisFX 1.44, Microsoft 0.65, Waves Audio 0.27, Package Cache 0.18, MySQL 0.07

### `C:\Windows` — 20.85 GB
- WinSxS 9.41 (component store, **hardlinked** — real disk use is lower)
- System32 5.18, servicing 1.13, SysWOW64 1.10, Installer 0.95, assembly 0.55, .NET 0.54, Fonts 0.41, Containers 0.37
- **SoftwareDistribution only 0.05 GB** — Windows Update cache is *not* the problem

---

## 7. Largest individual files (≥ 0.5 GB)

| GB | File |
|---:|---|
| 9.54 | `C:\pagefile.sys` (system managed, 8 GB RAM) |
| 1.65 | `C:\Users\MR.PC\.t3\userdata\statev2.sqlite` |
| 1.27 | `...\Adobe After Effects 2020\...\depth_estimator_384x384_low_vram.vrl` |
| 1.26 | `...\Brave-Browser\User Data\Default\File System\001\t\00\00000000` (site storage blob) |
| 0.77 | `...\AppData\Local\local-updater\installer.exe` |
| 0.71 | `C:\Program Files\WSL\system.vhd` |
| 0.57 | `...\Code\CachedExtensionVSIXs\openai.chatgpt-26.51021.22027-win32-x64` |
| 0.53 | `C:\Program Files\BraveSoftware\...\Installer\chrome.7z` |
| 0.49 | `C:\Program Files (x86)\Google\GoogleUpdater\crx_cache\...` |
| 0.40 | `...\@mmx-agentelectron-updater\MiniMax Code Setup 3.0.73.exe` |
| 0.37 | `...\t3code-updater\T3-Code-...-x64.exe` |

No giant ISO / VM disk / backup file exists anywhere on C:.

---

## 8. What is *not* the problem

- **Recycle Bin:** ~0.1 GB across all SIDs
- **Hibernation:** off, no `hiberfil.sys`
- **Windows.old / $Windows.~BT:** absent
- **Windows Update cache:** 0.05 GB
- **Temp folders:** user Temp 0.10 GB + Windows Temp 0.01 GB
- **System crash dumps:** `MEMORY.DMP` absent, Minidump 0 GB; only App CrashDumps 0.06 GB
- **WSL distros / Docker / Hyper-V images:** none (WSL feature is installed but has **no distros** — 1.14 GB idle)
- **VMware/VirtualBox disks:** none found
- **Downloads:** only 1.03 GB, largest file 0.12 GB

---

## 9. The ~10.9 GB that can't be seen without admin

Measured folders (136.55 GB) + pagefile/swap (9.56 GB) = 146.11 GB, while Windows reports 156.97 GB used → **10.88 GB unaccounted**.

Most likely, in order:
1. **System Restore / Volume Shadow Copies** — Windows 10 defaults to ~5% of the volume (≈10.4 GB on 208 GB). `DisableSR` is not set, and `RPSessionInterval = 1`, so System Protection is likely active. `vssadmin` and `Get-ComputerRestorePoint` both returned *Access denied* (not "none found").
2. **NTFS metadata** — MFT, `$LogFile`, `$UsnJrnl`, `$Bitmap` on a volume with millions of small files (node_modules, caches) can be 1–2 GB.
3. A few protected folders (`System Volume Information`, parts of `$WinREAgent`) that robocopy cannot read unprivileged.

This is **not necessarily waste** — restore points are your rollback safety net. Verify with an elevated prompt:
```powershell
vssadmin list shadowstorage
vssadmin list shadows
```
If shadow storage is near 10 GB and restore points are old, lowering the cap (not deleting files) reclaims it.

---

## 10. Reclaimable space — analysis only, nothing executed

### Tier A: pure caches, regenerate automatically (~26 GB)

| Category | GB | How it is normally reclaimed |
|---|---:|---|
| npm cache (`_cacache` + `_npx`) | 7.22 | `npm cache clean --force` |
| pnpm store | 1.53 | `pnpm store prune` |
| Bun install cache | 1.49 | `bun pm cache rm` |
| VS Code cached VSIX installers | 3.84 | clear `CachedExtensionVSIXs` (extensions stay installed) |
| VS Code Crashpad dumps | 1.11 | clear `Crashpad` |
| Chrome cache/SW/code cache | 2.61 | Chrome → Clear browsing data → Cached images and files |
| Brave cache/code cache/SW | 1.19 | same, in Brave |
| Updater installers + SquirrelTemp | 4.03 | delete downloaded setup copies |
| Adobe install temp (`C:\adobeTemp`) | 0.78 | leftover installer extraction folders |
| Stale Playwright builds (1223/1228/1522/1532) | 1.45 | keep only current browser revisions |
| pip / uv / node-gyp caches | 0.73 | `pip cache purge`, `uv cache clean` |

**Tier A total ≈ 25.9 GB.** These cost time to re-download, not data.

### Tier B: app-managed data, use the app's own setting (13–16 GB+)

| Item | GB | Note |
|---|---:|---|
| System Restore shadow storage | up to ~10 | adjust cap in System Protection; verify elevated first |
| WinSxS component cleanup | 1–3 est. | `Dism /Online /Cleanup-Image /StartComponentCleanup`; never delete by hand |
| Telegram `tdata` | 1.02 | Telegram → Settings → Advanced → Clear cache |
| Windows Search index | 0.70 | Indexing Options → rebuild (net gain is temporary) |
| WSL feature (no distros) | 1.14 | uninstall the optional feature if unused |

### Tier C: stale versions / duplicates (judgment call)

| Item | GB | Note |
|---|---:|---|
| After Effects 2020 + Premiere 2022 | 6.87 | only if you no longer use these versions — uninstall properly |
| WisprFlow `app-1.6.1400` + `app-1.6.1376` | 0.59 | old version left behind |
| `C:\mingw64` + `C:\MinGW` | 1.18 | two parallel MinGW installs |
| Rust `target` in `Desktop\bcode\aether` | 3.14 | rebuildable with `cargo build` |
| Adobe AE/Premiere 2023 stubs | ~0 | empty leftover folders |
| Installer archives in `Documents\Image-Line\Downloads` | 1.19 | re-downloadable |
| Telegram Downloads | 0.85 | media you chose to download |

### Tier D: do not touch

- `pagefile.sys` 9.54 GB — with only 8 GB RAM, shrinking it risks instability; leaving it system-managed is correct.
- `WinSxS`, `Windows\Installer` — manual deletion breaks servicing/repair.
- `AppData\Local\Programs`, installed apps, `.toolchain`, project sources, documents.
- `.t3\userdata\statev2.sqlite` — active T3 Code database.

---

## 11. Bottom line

- Free space today: **51.3 GB (24.6%)** — tight but not full; no emergency.
- 84 GB is your user profile, and **~26 GB of that is regenerable caches/installers** (npm/pnpm/bun 10.2, VS Code caches 5.0, browser caches 3.8, updater installers 4.8, old Playwright 1.4, pip/uv 0.7).
- 27 GB is applications (Adobe 7.3, Office 3.0, FL Studio 2.7, Edge stack 4.6, WSL 1.1).
- Performance-safe cleanup without touching personal files: **~26 GB immediately**, plus up to ~10 GB of restore points if you decide the cap is too generous. That would take free space to roughly **77 GB from caches alone**, or **~87 GB including restore points** — before uninstalling any app.
- Nothing was deleted. When you want, I can turn any tier into a step-by-step plan — with your approval before each command.

---

## 12. Method & caveats

- `robocopy <dir> <dummy> /L /E /BYTES /XJ` — list-only, no destination is created. Sizes are GiB (1 GB = 1024³ bytes), matching Explorer's "GB".
- Junction points/reparse points excluded (`/XJ`) to avoid double counting.
- `WinSxS` uses hardlinks: its 9.4 GB overlaps files also counted in System32/servicing. Real physical use is lower, which makes the true "unaccounted" gap slightly larger than 10.9 GB.
- Run as a normal (non-admin) user. `vssadmin`, `fsutil`, `DISM`, `Get-ComputerRestorePoint`, and `C:\System Volume Information` were therefore inaccessible.
- No third-party tools or packages were installed; Windows built-in PowerShell and robocopy were used exclusively. No dependency manifest is required. No cache files were written outside this folder.

---

## 13. Ghost apps — the app is gone, but its files remain

Follow-up scan: compared all **181 uninstall-registry entries** against the disk, tested every app executable, resolved all Start Menu shortcuts, checked services, scheduled tasks, startup entries, UWP package data, and `vswhere` for Visual Studio. Read-only.

### 13.1 Summary

| Leftover | GB | Hard evidence |
|---|---:|---|
| **Adobe ecosystem** (AE 2020, Premiere 2022, Photoshop 2024, shared data) | **10.45** | No Adobe uninstall entries; no Adobe Start Menu shortcuts; Photoshop 2024 has no `Photoshop.exe`; AE/Premiere still do |
| **Boris FX Sapphire / Continuum** | **1.48** | `C:\Program Files\BorisFX` is missing; all 7 Start Menu shortcuts are dead; only `ProgramData\BorisFX` presets remain |
| **Claude Desktop** | **1.47** | No executable anywhere, no uninstall entry; profile 0.89 GB + two 0.29 GB installers in `SquirrelTemp` |
| **Avast** | **0.89** | No service, no uninstall entry, definitions frozen at Aug 2024 |
| **MiniMax Code** | **0.82** | Two installers (400 + 370 MB) in `@mmx-agentelectron-updater`; no app executable |
| **Other leftovers** | **1.01** | Waves Central installers 0.50, Native Access 0.22, Avid 0.09, MySQL 0.07, TeamViewer 0.05, Steinberg 0.03, Office 2013 0.01, misc |
| **Visual Studio 2022 ghost** | **0.13** | Uninstall entry says 17.14.35 installed; `vswhere` returns `[]`; `devenv.exe` is missing |
| **Total** | **≈16.3** | |

These are **not** cache files. They are program folders, presets, profiles, installers, and registry traces left behind by apps that no longer exist in Apps & Features. Some are several years old; a cluster of folders was last modified on **16 Jul 2026**, consistent with a removal pass that did not clean up.

### 13.2 Adobe — 10.45 GB, unregistered but partly launchable

| Component | GB | State |
|---|---:|---|
| `C:\Program Files\Adobe\Adobe After Effects 2020` | 3.61 | `AfterFX.exe` exists (dated Dec 2020) |
| `C:\Program Files\Adobe\Adobe Premiere Pro 2022` | 3.26 | `Adobe Premiere Pro.exe` exists (dated Sep 2022) |
| Shared data & caches | 3.12 | `Roaming\Adobe` 1.61 (Common 0.73, UXP 0.70, CCX Welcome 0.18), `com.adobe.dunamis` 0.37, Common Files Adobe 0.35, `C:\adobeTemp` 0.78 |
| `Adobe Photoshop 2024` | 0.40 | Only a `Required` folder — **no `Photoshop.exe`**, broken partial install |
| AE/PS/Premiere 2023–2024 stub folders | 0.03 | Only empty `Support Files` / `Plug-ins` / `Lumetri` shells |

Registry keys under `HKLM\SOFTWARE\Adobe` and `HKCU\SOFTWARE\Adobe` remain (After Effects, Premiere Pro, Photoshop, Camera Raw, CSXS versions). There are **no uninstall entries** and **no Start Menu shortcuts**, which is why Windows shows no Adobe app installed.

If you still use After Effects 2020 or Premiere 2022, keep 6.87 GB and treat them as unregistered installs. If you don't, the whole 10.45 GB is recoverable — but remove it as a deliberate uninstall/cleanup, not by deleting random subfolders (Adobe keeps shared components in Common Files).

### 13.3 The non-Adobe leftovers

**Boris FX (1.48 GB)** — `ProgramData\BorisFX\Sapphire` 1.24 GB + `Continuum` 0.21 GB + emitter libraries 0.02 GB. The 7 shortcuts point to `C:\Program Files\BorisFX\...`, which no longer exists. Presets survive, the program does not.

**Claude Desktop (1.47 GB)** — `Roaming\Claude` 0.89 GB holds config, sessions, `claude_desktop_config.json`, cache and logs; `SquirrelTemp` 0.58 GB holds two full installer copies (`claude.exe` ×2) plus temp installs. No Claude Desktop executable or uninstall entry exists. **Do not confuse this with Claude Code CLI**, which is installed via npm (`Roaming\npm\node_modules\@anthropic-ai\claude-code`), has its own caches in `Local\claude-cli-nodejs` and `~/.claude`, and should stay.

**Avast (0.89 GB)** — `Program Files\Avast Software\Avast` 0.85 GB with definition sets from Aug 2024, `ProgramData\Avast Software` 16 MB, `C:\$AV_ASW` 5 MB, plus a renamed leftover driver `aswNetHub.sys.172318124353101` (0.5 MB). No Avast service, no uninstall entry. Windows Defender is the active AV.

**MiniMax Code (0.82 GB)** — updater folder has `MiniMax Code Setup 3.0.73.exe` (400 MB) and `installer.exe` (370 MB); `Roaming\MiniMax Agent` is 68 MB. No executable.

**Other 1.01 GB** —
- Waves Central installers 0.50 GB (the app folder is empty; the **plugin** data 0.28 GB stays because `WavesLocalServer.exe` is running and a WaveShell VST3 exists)
- Native Access 2 installers 0.22 GB (NTKDaemon itself is still installed)
- Avid shared files 0.09 GB, MySQL data 0.07 GB (may contain old databases — review first), TeamViewer 0.05 GB, Steinberg 0.03 GB
- Office 2013 `ClientX64` 0.01 GB, Topaz / VideoCopilot / GenArts / aescripts / Autokroma / YourPlugin ~0.03 GB, Tor config 0.01 GB, GRAMSEA 5 MB, VMware/Hard Disk Sentinel/AnyDesk/Windscribe ~0.01 GB

**Visual Studio 2022 (0.13 GB)** — registry keeps a full "Visual Studio Community 2022 17.14.35" uninstall entry, but `vswhere -all` returns `[]` and `devenv.exe` is absent. What remains is 48 MB of shell DLLs plus 80 MB under `ProgramData\Microsoft\VisualStudio\Packages`. Remove it through the **Visual Studio Installer** (it can unregister the stale instance) rather than deleting the folder.

### 13.4 Dead shortcuts, startup, services, tasks

- **14 dead shortcuts:** 7 Boris FX, 2 Python 3.12, 1 each Antigravity, Camoufox, Hermes, Wireshark, Audacity.
- **1 dead startup entry:** `HKCU\...\Run\Windscribe` → `C:\Program Files\Windscribe\Windscribe.exe` (missing).
- **0 services** and **0 scheduled tasks** point at missing executables — good.
- **UWP leftovers:** 22 package-data folders with no installed package, but only **0.4 MB** total — negligible.
- **Ghost folders (≤1 MB):** Avid, MySQL, Native Instruments (shell), TeamViewer, Waves Central, Side Effects, Steinberg, VMware, Hard Disk Sentinel, JewellozUtility, Windows Mail/Sidebar (system).

### 13.5 Checked and deliberately NOT counted as junk

| Item | GB | Why it stays |
|---|---:|---|
| Red Giant (Magic Bullet) | 0.36 | `Red Giant Service` is running from `C:\Program Files\Red Giant\Services` |
| Hermes `hermes-agent` | 1.26 | It is a **git repository + venv**, not an installed app; only its Start Menu shortcut is stale |
| Waves plugin data | 0.28 | `WavesLocalServer` running, WaveShell VST3 present — may still be used by FL Studio |
| Native Instruments / Kontakt | 0.03 | NTKDaemon installed; Kontakt data present |
| Spectrasonics / FabFilter / iZotope / Valhalla / XLN / Antares / Celemony | ~0.1 | Audio plugins usable from FL Studio, no uninstallers by design |
| Java JDK 21 | 0.33 | Active — `java.exe` resolves to this folder |
| Claude Code / OpenAI Codex / Plannotator / `agy.exe` | — | Working CLI tools with active binaries |
| Antigravity remnants | 0.58 | Desktop shortcut is dead, but `agy.exe` (Nov 2026) is active; updater 0.32 + old `agy.exe.old` 0.19 are the only clear leftovers |

### 13.6 System-file leftovers (not app data)

| Item | Size | Assessment |
|---|---:|---|
| `System32\DriverStore\FileRepository` | 2.05 GB | Active driver store; Intel iGPU package alone 1.28 GB. Not orphaned — do not delete |
| `Windows\Installer` | 0.96 GB (116 MSI/MSP) | Repair/uninstall cache. Deleting breaks uninstallers |
| `Windows\WinSxS` | 9.41 GB apparent | Hardlinked component store; only DISM cleanup is safe |
| `Program Files\WSL` | 1.14 GB | WSL feature installed, **no distros** — unused, not broken |
| `Windows\Containers` | 0.37 GB | Windows feature; state needs admin to query |
| `ProgramData\Package Cache` + VS packages | 0.26 GB | .NET/VC++ repair cache, mostly tied to installed runtimes |
| `Program Files\Microsoft Office 15` | 0.01 GB | Office 2013 shell leftover next to Office 2024 |
| Dead Avast driver file | 0.0005 GB | Renamed `.sys` leftover, not loaded |

### 13.7 What this changes

- **Caches (Tier A): ~26 GB.**
- **Ghost-app files: ~16.3 GB**, of which **~6.9 GB is still-launchable Adobe** (AE 2020 + Premiere 2022). If those are still used, only **~9.4 GB** of this section is clearly removable.
- There is **~2 GB of overlap** with Tier A (the updater installers for MiniMax, Waves Central, Native Access, Antigravity and Claude's SquirrelTemp were already counted there). Don't add the two totals blindly.
- Combined realistic potential: **~33 GB if AE/Premiere stay**, **~40 GB if they go**. Nothing here requires touching documents, projects, or personal data.

The biggest single insight: **Windows has no idea Adobe is installed (10.45 GB), but After Effects 2020 and Premiere 2022 still have working executables.** That is the classic "app is gone from the list, files are still there" case — along with Avast, Boris FX, Claude Desktop, MiniMax, and the Visual Studio ghost registration.

---

## 15. Actions taken (12 Nov 2026)

Deleted, with elevation, the five categories the user approved: **Adobe, Boris FX, Claude Desktop, Avast, MiniMax Code**. All 17 paths in section 14.1–14.5 and 14.8 are gone. Free space: 47.61 GB → 62.40 GB (+14.79 GB).

- `C:\Program Files\Avast Software` needed `takeown` + `icacls` (TrustedInstaller-owned); removed on the second pass.
- Claude Code CLI, Codex, Plannotator, `agy.exe`, Java, Red Giant, Waves, Native Instruments, Hermes, and all other items in section 13.5 were left untouched.
- Registry keys for the removed apps were not cleaned (only files/folders). Run `regedit` or a cleanup tool manually if desired.
- Visual Studio 2022 ghost registration, stale Antigravity files, and all other leftovers in sections 14.6–14.12 remain.

---

## 14. Exact paths of every ghost-app file

All paths verified to exist on 12 Nov 2026 unless marked **missing**. Sizes are measured, not estimated. Nothing here has been deleted.

### 14.1 Adobe — 10.4 GB

| Path | Size | State |
|---|---:|---|
| `C:\Program Files\Adobe\Adobe After Effects 2020` | 3,700 MB | **`Support Files\AfterFX.exe` works** (Dec 2020) |
| `C:\Program Files\Adobe\Adobe Premiere Pro 2022` | 3,336 MB | **`Adobe Premiere Pro.exe` works** (Sep 2022) |
| `C:\Users\MR.PC\AppData\Roaming\Adobe` | 1,644 MB | Common 0.73, UXP 0.70, CCX Welcome 0.18 |
| `C:\adobeTemp` | 799 MB | installer temp, root of C: |
| `C:\Program Files\Adobe\Adobe Photoshop 2024` | 398 MB | **broken** — only `Required`, no `Photoshop.exe` |
| `C:\Users\MR.PC\AppData\Roaming\com.adobe.dunamis` | 380 MB | Adobe installer/telemetry data |
| `C:\Program Files (x86)\Common Files\Adobe` | 304 MB | shared 32-bit components |
| `C:\Program Files\Common Files\Adobe` | 50 MB | shared 64-bit components |
| `C:\Program Files\Adobe\Adobe After Effects 2024` | 33 MB | stub (empty `Support Files`) |
| `C:\ProgramData\Adobe` | 3.4 MB | logs/config |
| `C:\Program Files\Adobe\Adobe After Effects 2023` | 0.8 MB | stub |
| `C:\Program Files\Adobe\Adobe Photoshop 2023` | 0.2 MB | stub |
| `C:\Program Files\Adobe\Adobe Premiere Pro 2023` | 0.1 MB | stub (only `Lumetri`) |

### 14.2 Boris FX — 1.48 GB

| Path | Size | State |
|---|---:|---|
| `C:\ProgramData\BorisFX\Sapphire` | 1,243 MB | presets/content, plugin gone |
| `C:\ProgramData\BorisFX\Continuum` | 210 MB | presets/content, plugin gone |
| `C:\ProgramData\BorisFX\EmitterLibraries` | 24 MB | particle emitters |
| `C:\Users\MR.PC\AppData\Local\BorisFX` | 1.3 MB | local cache |
| `C:\Program Files\BorisFX` | **missing** | target of all 7 Start Menu shortcuts |

### 14.3 Claude Desktop — 1.50 GB (Claude Code is separate and stays)

| Path | Size | State |
|---|---:|---|
| `C:\Users\MR.PC\AppData\Roaming\Claude` | 911 MB | profile, sessions, cache, logs |
| `C:\Users\MR.PC\AppData\Local\SquirrelTemp` | 588 MB | two full `claude.exe` installer copies |
| `C:\ProgramData\Claude` | 0.1 MB | logs |

### 14.4 Avast — 0.89 GB

| Path | Size | State |
|---|---:|---|
| `C:\Program Files\Avast Software` | 869 MB | definitions frozen Aug 2024 |
| `C:\ProgramData\Avast Software` | 16 MB | persistent data |
| `C:\$AV_ASW` | 5.3 MB | scan/update leftovers |
| `C:\Windows\System32\drivers\aswNetHub.sys.172318124353101` | 0.5 MB | renamed driver, not loaded |

### 14.5 MiniMax Code — 0.84 GB

| Path | Size | State |
|---|---:|---|
| `C:\Users\MR.PC\AppData\Local\@mmx-agentelectron-updater` | 770 MB | `installer.exe` 370 MB + `pending\MiniMax Code Setup 3.0.73.exe` 400 MB |
| `C:\Users\MR.PC\AppData\Roaming\MiniMax Agent` | 69 MB | Chromium profile, no executable |

### 14.6 Other vendor leftovers — ~1.0 GB

| Path | Size | State |
|---|---:|---|
| `C:\Users\MR.PC\AppData\Local\central-updater` | 510 MB | Waves Central installers; `C:\Program Files\Waves Central` is empty |
| `C:\Users\MR.PC\AppData\Local\nativeaccess2-updater` | 227 MB | `installer.exe` 146 + `pending\temp-Native-Access.exe` 81 |
| `C:\Program Files\Common Files\Avid` | 95 MB | shared Avid files |
| `C:\ProgramData\MySQL` | 67 MB | **may contain old databases — inspect before touching** |
| `C:\Program Files (x86)\Steinberg` | 28 MB | shared components |
| `C:\Users\MR.PC\AppData\Roaming\YourCompany` | 17 MB | unknown plugin |
| `C:\Users\MR.PC\AppData\Roaming\tor` | 12 MB | Tor config/keys |
| `C:\Program Files\Microsoft Office 15` | 10 MB | Office 2013 shell next to Office 2024 |
| `C:\Users\MR.PC\AppData\Roaming\GRAMSEA` | 5.4 MB | old Electron app profile |
| `C:\ProgramData\Topaz Labs LLC` | 3.8 MB | plugin data |
| `C:\ProgramData\VideoCopilot` | 3.6 MB | plugin data |
| `C:\Program Files\Waves Central` | 0 MB | empty shell |
| `C:\Program Files\Native Instruments\Native Access` | 0 MB | empty shell |
| `C:\Program Files\TeamViewer` | 0 MB | empty shell |
| `C:\Program Files\Avid`, `C:\Program Files\MySQL`, `C:\Program Files\Steinberg` | 0 MB | empty shells |
| `C:\Program Files (x86)\VMware`, `C:\Program Files (x86)\Hard Disk Sentinel`, `C:\Program Files (x86)\JewellozUtility` | 0 MB | empty shells |
| `C:\ProgramData\GenArts`, `C:\ProgramData\aescripts`, `C:\ProgramData\Autokroma` | ~0 MB | empty folders |

### 14.7 Visual Studio 2022 ghost registration — 0.12 GB

| Path | Size | State |
|---|---:|---|
| `C:\Program Files\Microsoft Visual Studio\2022\Community` | 48 MB | `devenv.exe` missing |
| `C:\ProgramData\Microsoft\VisualStudio\Packages` | 80 MB | installer payload cache |
| `C:\Program Files (x86)\Microsoft Visual Studio\Installer` | 124 MB | installer itself, still functional |

Remove via **Visual Studio Installer**, not by deleting folders — `vswhere -all` already returns `[]`.

### 14.8 Stale Antigravity files — 0.52 GB *(not in the 16.3 GB total)*

| Path | Size | State |
|---|---:|---|
| `C:\Users\MR.PC\AppData\Local\antigravity-updater` | 322 MB | installer cache, app folder is empty |
| `C:\Users\MR.PC\AppData\Local\agy\bin\agy.exe.1794405473000014300.old` | 195 MB | previous CLI binary |
| `C:\Users\MR.PC\AppData\Local\antigravity` | 4.3 MB | old app data |
| `C:\Users\MR.PC\AppData\Local\Programs\antigravity` | 0 MB | empty |

`C:\Users\MR.PC\AppData\Local\agy\bin\agy.exe` (216 MB, Nov 2026) is **active — keep it**.

### 14.9 Dead shortcuts (14)

```
C:\ProgramData\Microsoft\Windows\Start Menu\Programs\Audacity.lnk                                  -> D:\Audacity\Audacity.exe
C:\ProgramData\Microsoft\Windows\Start Menu\Programs\Wireshark.lnk                                 -> C:\Program Files\Wireshark\Wireshark.exe
C:\ProgramData\Microsoft\Windows\Start Menu\Programs\Boris FX Sapphire 2023.5 Adobe\*.lnk           -> C:\Program Files\BorisFX\...   (7 files)
C:\Users\MR.PC\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Antigravity.lnk               -> ...\Programs\antigravity\Antigravity.exe
C:\Users\MR.PC\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Camoufox Private Browsing.lnk -> ...\camoufox\...\private_browsing.exe
C:\Users\MR.PC\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Hermes.lnk                    -> ...\hermes-agent\...\Hermes.exe
C:\Users\MR.PC\AppData\Roaming\Microsoft\Windows\Start Menu\Programs\Python 3.12\*.lnk              -> ...\Programs\Python\Python312\python.exe   (3 files)
```

The 7 Boris FX shortcuts: `Activate Sapphire AE License`, `Check for Sapphire AE Updates`, `Install Sapphire AE RLM License`, `Sapphire AE Effect Builder`, `Sapphire AE Flare Designer`, `Online Help (HTML)`, `Online Help (PDF)`.

Python 3.12 is genuinely gone — only Python 3.13 (257 MB) and 3.14 (1,210 MB) are installed, both alive. Camoufox is fully gone (only the shortcut remains).

### 14.10 Dead startup entry (1)

```
HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\Run
  Windscribe = "C:\Program Files\Windscribe\Windscribe.exe" -os_restart
```

`C:\Program Files\Windscribe\Windscribe.exe` does not exist.

### 14.11 Paths that look orphaned but are NOT

| Path | Size | Why it stays |
|---|---:|---|
| `C:\Users\MR.PC\AppData\Local\hermes` | 1,292 MB | git repo + venv; only its shortcut is stale |
| `C:\Program Files\Red Giant` | 362 MB | `Red Giant Service` is **running** from `Services\Red Giant Service.exe` |
| `C:\Users\MR.PC\AppData\Local\Java\jdk-21.0.12.1+1` | 328 MB | `java.exe` resolves here |
| `C:\ProgramData\Waves Audio` | 273 MB | `WavesLocalServer.exe` is running; WaveShell VST3 present |
| `C:\Users\MR.PC\AppData\Local\agy\bin\agy.exe` | 216 MB | active CLI, Nov 2026 |
| `C:\Users\MR.PC\AppData\Local\Programs\Local` | 2,161 MB | LocalWP installed and current |
| `C:\Users\MR.PC\AppData\Local\Programs\Python\Python313` / `Python314` | 257 / 1,210 MB | both installed |
| `C:\Users\MR.PC\AppData\Local\openai\Codex` | 280 MB+ | Codex CLI active |
| `C:\Program Files\Common Files\VST3` | 126 MB | audio plugins used by FL Studio |

### 14.12 System paths from 13.6 (never delete directly)

```
C:\Windows\System32\DriverStore\FileRepository      2.05 GB   active driver store
C:\Windows\Installer                                  957 MB   116 MSI/MSP, needed to uninstall/repair
C:\Windows\WinSxS                                   9.41 GB   apparent; hardlinked, DISM only
C:\Program Files\WSL                                1.14 GB   feature installed, no distros
C:\Windows\Containers                                0.37 GB   Windows feature
C:\ProgramData\Package Cache                          175 MB   .NET/VC++ repair cache
```
