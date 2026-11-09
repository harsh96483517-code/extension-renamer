# Extension Renamer

A native Android app that changes the **file extension** of many files at once — `.jpg → .png`,
`.txt → .pdf`, `.webm → .mp4`, anything you type — while leaving the file name itself untouched.

Built with Kotlin + Jetpack Compose (Material 3), minimum Android 8.0, 100% offline, and **zero
runtime permissions**: every file operation goes through the Storage Access Framework.

## Download the APK

The APK is built by GitHub Actions, so you do not need Android Studio to install the app:

1. Open the **Actions** tab of this repository.
2. Pick the latest green **Build APK** run.
3. Download the **ExtensionRenamer-debug-apk** artifact, unzip it and install `app-debug.apk`
   on your phone (allow "Install unknown apps" for your file manager when prompted).

The debug APK is a normal, installable build — no Play Store account needed.

**Direct link (always the newest build):**
`https://github.com/harsh96483517-code/extension-renamer/releases/download/latest/app-debug.apk`

Every push to `main` re-publishes the APK on the `latest` release, so this URL always points at
the newest working build.

## Features

- **Bulk rename** — pick any number of files and change all their extensions in one go.
- **Two pickers** — `Select files` (multi-select via `ACTION_OPEN_DOCUMENT`) or
  `Select folder` (whole directory via `ACTION_OPEN_DOCUMENT_TREE`, recommended for large batches).
- **Any file type** — JPG, PNG, WEBP, PDF, MP4, ZIP, TXT, DOCX, APK… the app never parses the
  content, it only edits the display name, so nothing is transcoded or corrupted.
- **Custom extension** — free text input plus one-tap chips for common types.
- **Live preview** — every row shows `old_name.ext → new_name.ext` before anything is touched.
- **Per-file selection** — checkboxes, select all / clear, and remove from list.
- **Names preserved** — only the text after the last dot changes (`holiday.2024.tar.gz → holiday.2024.tar.zip`).
- **No overwrites** — the planner reads the folder listing and refuses to rename onto an existing
  name. Optional auto-numbering turns clashes into `name (1).ext`, `name (2).ext`.
- **Undo** — every run is stored in local history and can be reverted with one tap, even after a restart.
- **Reports** — a summary dialog lists successes and the exact reason for every failure.
- **Large selections** — listing and renaming are streamed with a progress bar; the UI never blocks.
- **No permissions, no internet** — the manifest declares no permissions at all and the app has no
  network code.

## How renaming actually works

`FileRenamer` uses `DocumentsContract.renameDocument()` first: the provider renames the document
in place, so the document id, timestamps and any MediaStore entry stay consistent.

If a provider does not implement rename (some cloud and download providers return `null`), the app
falls back to **create → copy → flush → delete original**, and only deletes the original after the
copy has been written successfully.

Clash detection reads the sibling file names of each target folder. That listing is available when
you pick a **folder**; with plain file picks some providers do not expose the parent directory, so
the app warns you on screen that clash detection is unavailable for those rows.

## Project layout

```
app/src/main/java/com/harsh/extensionrenamer/
├── MainActivity.kt                 Compose entry point
├── data/
│   ├── SelectedFile.kt             picked-file model
│   ├── FileRenamer.kt              SAF rename engine + folder listing
│   ├── RenamePlanner.kt            preview + clash/auto-number planning
│   └── HistoryStore.kt             JSON undo history in app storage
├── util/FileNameUtils.kt           base name / extension helpers
├── viewmodel/RenamerViewModel.kt   state, rename + undo orchestration
└── ui/
    ├── AppRoot.kt                  pickers + screen routing
    ├── theme/                      Material 3 light + dark scheme (purple/blue)
    ├── screens/HomeScreen.kt       hero, extension picker, file list, action bar
    ├── screens/HistoryScreen.kt    rename history + undo
    └── components/ReportDialog.kt  success/failure report
```

## Build it yourself

Requirements: Android Studio (Koala or newer) or a command line with **JDK 17** and
**Android SDK 34**.

```bash
# debug APK
./gradlew :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk

# release APK (add your own signing config first)
./gradlew :app:assembleRelease
```

If the Gradle wrapper JAR is missing in your checkout, run `gradle wrapper --gradle-version 8.7`
once, or just open the project in Android Studio and let it sync — it regenerates the wrapper.

The GitHub workflow builds with Gradle 8.7 + JDK 17 and uploads the debug APK as an artifact.

## Tech

- Kotlin 1.9.24, AGP 8.5.2, Gradle 8.7, Compose BOM 2024.06.00, Material 3
- `minSdk 26` (Android 8.0), `targetSdk 34`
- No third-party runtime libraries beyond AndroidX — history is persisted with `org.json`
