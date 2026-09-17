# NAS Drive - Session Progress Notes

Status as of 2026-09-17 (second session, on Linux). Read this first when picking the
project back up.

## What this is

Android app (Kotlin + Jetpack Compose) that browses a NAS SMB share and copies files
in both directions. Meant to be used over Tailscale: point it at the NAS's Tailscale
IP/MagicDNS name, and it talks plain SMB over that tunnel. See [README.md](README.md)
for the user-facing setup/usage doc.

## Current state: fully working end-to-end, verified against the real NAS

Connect, browse (including nested folders), download, upload, move, and delete were all
tested live against naspi (192.168.1.43, share `nas`) over LAN and confirmed working.
Three real bugs were found and fixed this session (below), and move/delete were added
as new features after the user asked for them.

## Move and delete (added this session)

Every row (file or folder) now has a move icon and a delete icon next to the existing
download icon:

- **Delete**: `IconButton` -> `AlertDialog` confirmation (wording differs for
  folders - warns that everything inside gets deleted too) -> `SmbRepository.delete()`
  -> `SmbFile.delete()`. jcifs-ng's `delete()` on a directory recurses on its own,
  there's no separate "must be empty" mode.
- **Move**: tapping the move icon puts `UiState.move` into a non-null `MoveState`
  (which entry, and which path it started in). While that's set, `BrowserScreen` shows
  a banner ("Moving X... currently: /path") instead of the per-row action icons, but
  folder navigation (`onOpenFolder`/`onNavigateUp`) still works normally underneath it -
  that's how you "browse to a destination". "Move here" calls
  `SmbRepository.move(sourcePath, destPath)` -> `SmbFile.renameTo()`. "Cancel" just
  clears `move` back to null. No cross-share moves, no moving multiple things at once -
  kept to the one-entry-at-a-time shape the rest of the app already has.

Verified against the real NAS with throwaway test files (created via SSH, moved/deleted
through the app, confirmed via SSH, cleaned up) rather than risking real media -
worth doing the same if touching this again.

## Toolchain now also set up on Linux

In addition to the Windows toolchain notes further down (still valid for that machine),
this project was built successfully on an Arch/Omarchy Linux desktop:

- `sudo pacman -S jdk17-openjdk android-tools` (JDK 17 + adb from official repos).
- Android SDK cmdline-tools (same known-good build 11076708) and Gradle 8.7 fetched as
  plain archives into `~/Android-Toolchain/`, same layout as the Windows instructions,
  just with the Linux cmdline-tools zip
  (`commandlinetools-linux-11076708_latest.zip`) instead of the Windows one.
- Build command is the same shape as Windows, just bash instead of PowerShell:
  ```bash
  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
  export ANDROID_HOME=~/Android-Toolchain/android-sdk
  echo "sdk.dir=$ANDROID_HOME" > local.properties
  ~/Android-Toolchain/gradle-8.7/bin/gradle assembleDebug --no-daemon
  ```
- **Gotcha**: the debug keystore is machine-specific
  (`~/.config/.android/debug.keystore` on this box - note `.config`, not the usual
  `~/.android`). A debug APK built on a different machine has a different signature,
  so `adb install -r` on top of an existing install fails with
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Fix: `adb uninstall com.kmarko.nasdrive` first
  (wipes the app's saved SMB credentials, nothing else) then install fresh.

## Three real bugs found and fixed this session

1. **Nested folders (3+ levels deep) always failed** with "The system cannot find the
   path/file specified." Root cause: `SmbRepository.kt`'s `buildUrl()` was
   percent-encoding each path segment (`URLEncoder.encode` - spaces to `%20`, etc.)
   before handing the URL to jcifs-ng. jcifs-ng 2.1.10's `SmbFile(String, CIFSContext)`
   does **not** URL-decode the path - it wants literal component names. Since real
   movie/show folder names virtually always contain a space (at minimum the
   `(YYYY)` year), this broke every folder more than two levels deep
   (`share/Media/Movies` worked since neither segment needed encoding; anything
   inside `Movies` didn't). Verified with a standalone jcifs-ng test program against
   the live NAS with both encoded (fails) and raw (works) paths - see the fix, the
   `encodeSegment`/`URLEncoder` step is gone, segments are now passed through as-is.
2. **Failed folder opens corrupted navigation state.** `NasBrowserViewModel.openFolder`
   updated `currentPath` *before* attempting the listing, and never rolled it back on
   failure. Combined with bug #1, tapping several folders in a row (each failing)
   silently concatenated their names onto one broken path
   (`Media/Movies/A/B/C/D`), which is why it looked like "literally every folder is
   broken" - once one navigation fails, all subsequent taps compound on the wreckage.
   Fixed: `currentPath` now only advances after `repo.list()` succeeds.
3. **The last file's Download button could get permanently hidden** under the
   floating "Upload" button with no way to scroll past it - `BrowserScreen.kt`'s
   `LazyColumn` had no bottom content padding, so the FAB just floated on top of the
   list instead of reserving space. Fixed with
   `contentPadding = PaddingValues(bottom = 96.dp)`.

Diagnosis method worth remembering: `adb shell uiautomator dump` + `adb exec-out
screencap -p` to drive/inspect the UI blind from the CLI, `smbclient` and a small
standalone jcifs-ng test program (pulling the jar straight from Maven Central) to
isolate client-library bugs from server-side ones, and SSH to naspi to check the
actual filesystem/Samba config directly rather than guessing.

## Build toolchain

**This was set up under `C:\Android-Toolchain` and `C:\Program Files\Microsoft\...`
on the ORIGINAL machine - those paths do NOT exist on a new machine.** If you're
continuing on different hardware, redo the toolchain setup below there; nothing
under those paths needs to move, only this project folder does. The Kotlin/Compose
source in this repo is unaffected either way.

The original machine had no Android Studio, JDK, or Gradle, and no admin rights
(`choco install` failed outright with a permissions error even without trying to
elevate - don't bother with choco). Everything below was installed without admin
rights, as plain zip extracts plus one winget MSI that happened to work.

### Setting up the toolchain on a fresh machine

1. **JDK 17** - try winget first (worked on the original machine despite warning
   about a UAC prompt):
   ```powershell
   winget install --id Microsoft.OpenJDK.17 -e --silent --accept-package-agreements --accept-source-agreements
   ```
   Installs to something like `C:\Program Files\Microsoft\jdk-17.x.x.x-hotspot`
   (check the exact folder name after install - version number will differ).
   If winget/admin isn't available, download the portable zip instead (no installer,
   no admin needed) from the Microsoft OpenJDK download page
   (https://learn.microsoft.com/en-us/java/openjdk/download - grab the Windows x64
   `.zip`, not `.msi`/`.exe`) and extract it anywhere writable, e.g.
   `C:\Android-Toolchain\jdk17`.

2. **Android SDK cmdline-tools** - use this exact known-good older release, build
   11076708 ("cmdline-tools;11.0"), which has the classic Java `sdkmanager.bat`:
   ```powershell
   $toolchain = "C:\Android-Toolchain"
   New-Item -ItemType Directory -Force -Path $toolchain
   Invoke-WebRequest -Uri "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip" -OutFile "$toolchain\cmdline-tools.zip"
   # sha1 checksum should be 3d2917302740f476999a091bc5558837c7a863c5 - verify with:
   # (Get-FileHash "$toolchain\cmdline-tools.zip" -Algorithm SHA1).Hash
   Expand-Archive -Path "$toolchain\cmdline-tools.zip" -DestinationPath "$toolchain\cmdline-tools-tmp" -Force
   New-Item -ItemType Directory -Force -Path "$toolchain\android-sdk\cmdline-tools"
   Move-Item -Path "$toolchain\cmdline-tools-tmp\cmdline-tools" -Destination "$toolchain\android-sdk\cmdline-tools\legacy"
   Remove-Item -Recurse -Force "$toolchain\cmdline-tools-tmp"
   ```
   **Do NOT grab whatever is currently "latest" from
   https://developer.android.com/studio#command-tools** - as of 2026-09 that page
   points to build 16111833 ("cmdline-tools;22.0"), whose new unified `android` CLI
   is broken (`android sdk list` crashes with exit code -1073740791; `android sdk
   install build-tools@34.0.0` / `platforms@android-35` fail inconsistently with
   "No url for X" or "Package not found"). If you want to double check what's
   current, cross-reference `https://dl.google.com/android/repository/repository2-3.xml`
   (search for `<remotePackage path="cmdline-tools;11.0">` and its sibling
   `<url>commandlinetools-win-...</url>` / `<checksum>` - the download page's own
   link may also 404, it's served from an expiring CDN redirect).

3. **Accept SDK licenses and install packages** (adjust `$env:JAVA_HOME` to the
   actual JDK folder from step 1):
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.x.x.x-hotspot"
   $env:ANDROID_HOME = "C:\Android-Toolchain\android-sdk"
   $sdkmanager = "C:\Android-Toolchain\android-sdk\cmdline-tools\legacy\bin\sdkmanager.bat"

   # PowerShell piping "y" into sdkmanager directly does NOT reliably accept all
   # licenses (it stops partway). Use a file + cmd.exe stdin redirection instead:
   1..30 | ForEach-Object { "y" } | Out-File -Encoding ascii "C:\Android-Toolchain\yes.txt"
   cmd /c "`"$sdkmanager`" --licenses --sdk_root=`"$env:ANDROID_HOME`" < `"C:\Android-Toolchain\yes.txt`""

   & $sdkmanager --sdk_root="$env:ANDROID_HOME" "platform-tools" "build-tools;34.0.0" "platforms;android-35"
   ```

4. **Gradle 8.7** (plain zip, no installer):
   ```powershell
   Invoke-WebRequest -Uri "https://services.gradle.org/distributions/gradle-8.7-bin.zip" -OutFile "C:\Android-Toolchain\gradle8.7.zip"
   Expand-Archive -Path "C:\Android-Toolchain\gradle8.7.zip" -DestinationPath "C:\Android-Toolchain" -Force
   ```
   Ends up at `C:\Android-Toolchain\gradle-8.7`. This project's `gradlew`/`gradlew.bat`
   reference a wrapper jar that was never generated, so build with the extracted
   Gradle directly (see below) rather than `./gradlew`, unless you open the project
   in Android Studio (which can regenerate the wrapper itself).

### How to build

```powershell
Set-Location "<path to this DRIVE APP folder on the new machine>"
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-17.x.x.x-hotspot"
$env:ANDROID_HOME = "C:\Android-Toolchain\android-sdk"
"sdk.dir=C:\\Android-Toolchain\\android-sdk" | Out-File -FilePath "local.properties" -Encoding ascii
& "C:\Android-Toolchain\gradle-8.7\bin\gradle.bat" assembleDebug --no-daemon
```

APK lands at `app\build\outputs\apk\debug\app-debug.apk`.

### How to install on the phone

```
C:\Android-Toolchain\android-sdk\platform-tools\adb.exe devices
C:\Android-Toolchain\android-sdk\platform-tools\adb.exe install -r "<path>\app\build\outputs\apk\debug\app-debug.apk"
```

Requires USB debugging enabled on the phone (Settings > About phone > tap Build
number 7x > Developer options > USB debugging) and accepting the "Allow USB
debugging" prompt on the phone screen. On the original machine, the first USB
connection attempt showed Windows-level "Unknown USB Device (Device Descriptor
Request Failed)" and `adb devices` came up empty - swapping the USB cable/port
fixed it. If that happens again, try a different cable/port before assuming it's
a software problem. Alternatively, wireless ADB over the phone's "Developer
options > Wireless debugging" works too and avoids cables entirely (useful since
Tailscale is already in play for this project anyway).

## One bug fixed during the build

`ConnectionScreen.kt` used `TopAppBar` (Material3) without the required
`@OptIn(ExperimentalMaterial3Api::class)` annotation - added, matching what
`BrowserScreen.kt` already had. That was the only real compile error.

## Known loose ends / possible next steps

- 3 harmless deprecation warnings on build: `Icons.Filled.ArrowBack`,
  `Icons.Filled.Logout`, `Icons.Filled.InsertDriveFile` should move to
  `Icons.AutoMirrored.Filled.*`. Cosmetic only, doesn't block anything.
- No Android Studio installed - only the CLI toolchain above. Fine for
  command-line builds/adb installs, but editing/debugging comfortably would
  benefit from Android Studio if the user ever wants the GUI.
- `gradlew`/`gradlew.bat` wrapper scripts exist with `gradle-wrapper.properties`
  pointing at Gradle 8.7, but no `gradle-wrapper.jar` was generated - opening in
  Android Studio should self-heal this; the CLI build above bypasses it entirely.
