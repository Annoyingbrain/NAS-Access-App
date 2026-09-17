# NAS Drive

A minimal Android app (Kotlin + Jetpack Compose) for browsing a NAS share over SMB and
copying files in both directions. It's designed to be used over Tailscale: point it at
your NAS's Tailscale IP or MagicDNS name, and it talks plain SMB over that tunnel.

## What it does

- Connect screen: host, port, SMB share name, username, password, optional domain/workgroup.
  Credentials are saved locally in `EncryptedSharedPreferences`.
- Browser screen: navigate folders on the share, download any file to your phone (via the
  system "Save As" picker), upload any file from your phone to the current folder (via the
  system file picker). Simple progress indicator during transfers.
- Move and delete: every row has a move icon and a delete icon (files and folders both).
  Move puts the app into a "pick a destination" mode — browse to the target folder and tap
  "Move here". Delete asks for confirmation first; deleting a folder removes everything
  inside it too.

## Requirements

- Android Studio (Koala/2024.1 or newer recommended).
- Tailscale installed and connected on the phone, with the NAS joined to the same tailnet
  (or exposed via a subnet router).
- SMB (CIFS) file sharing enabled on the NAS, with a share and a user account that has
  read/write permission to it.

## Opening the project

1. Open Android Studio -> **Open** -> select the `DRIVE APP` folder.
2. If prompted about a missing Gradle wrapper, let Android Studio generate it
   (it will use the Gradle version pinned in `gradle/wrapper/gradle-wrapper.properties`).
3. Let Gradle sync. First sync will download dependencies (AndroidX, Compose, `jcifs-ng`).
4. Run on a device/emulator that also has network access to the NAS (an emulator needs
   Tailscale support via a bridged/host network setup, so testing on a real phone with the
   Tailscale app is the easy path).

If dependency resolution fails on the pinned `jcifs-ng` version (`2.1.10`), open
[app/build.gradle.kts](app/build.gradle.kts) and bump it to whatever the latest `eu.agno3.jcifs:jcifs-ng`
release is on Maven Central — the API used here has been stable across recent releases.

## First run

1. Launch the app, enter the NAS's Tailscale address (e.g. `100.x.x.x` or
   `nas-hostname.your-tailnet.ts.net`), the share name, and credentials.
2. Tap **Connect**. On success you'll land in the file browser at the share root.
3. Tap a folder to open it, the back arrow in the top bar to go up a level.
4. Tap the download icon next to a file to save it to your phone (choose the destination
   in the system picker — defaults work fine for saving to Downloads).
5. Tap the **Upload** button to pick a file from your phone and send it to the folder
   you're currently viewing on the NAS.
6. Tap the move icon on a file/folder, browse to where you want it, then tap **Move here**
   (or **Cancel** to back out). Tap the delete icon to remove a file/folder, after confirming.

## Notes / things you may want to extend later

- No multi-select or batch transfer — one file at a time, kept intentionally simple.
- No thumbnail previews or file type icons beyond folder/file.
- If your NAS enforces SMB signing or a specific SMB version range, adjust the
  `jcifs.smb.client.minVersion` / `maxVersion` properties in
  [SmbRepository.kt](app/src/main/java/com/kmarko/nasdrive/SmbRepository.kt).
