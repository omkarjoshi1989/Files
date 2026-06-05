<div align="center">

# 📁 Files — Android File Manager

**A powerful, beautiful, and feature-rich file manager for Android built entirely with Jetpack Compose & Material 3.**

![Android](https://img.shields.io/badge/Android-API%2024%2B-brightgreen?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-blueviolet?logo=kotlin)
![Material 3](https://img.shields.io/badge/Material%203-Design-blue?logo=materialdesign)
![Media3](https://img.shields.io/badge/Media3-ExoPlayer-orange?logo=google)

</div>

---

## ✨ What Makes This App Stand Out

A fully native Android file manager that goes beyond just listing files. It combines a polished file explorer with a capable media player, music player, PDF viewer, and security layer — all in one app, built with modern Android development practices.

---

## 📂 File Explorer

- 🗂️ **Full folder navigation** — tap any folder to enter, swipe back or tap the back arrow to go up
- 🍞 **Scrollable breadcrumb bar** — always shows your current path from root; tap any segment to jump directly; `/storage/emulated/0` collapses to "Internal Storage"
- 🔍 **Inline search** — search within the current folder in real time
- 👁️ **Show / hide hidden files** — toggle visibility of dot-prefixed files and folders; hidden items render at 50% opacity so they're clearly distinguishable
- 📄 **Full filenames with extensions** — up to 2-line wrap so nothing gets truncated
- 📊 **File metadata** — file size, last modified date; folders show item count and direct-file sizes
- 🎞️ **Video playback progress** — video files show a thin progress bar + percentage under the filename so you know where you left off at a glance
- ⭐ **Favorite star badge** — starred files display a gold star badge right in the file list
- 🔢 **Paginated loading** — loads 100 items at a time; more items load automatically as you scroll down
- 🔄 **Pull-to-refresh** — pull down anywhere in a folder to reload its contents instantly

### 🗃️ File Operations

- ✂️ **Cut / Copy / Paste** — long-press or use multi-select; a floating "Paste here" button appears in the destination folder
- ✏️ **Rename** — inline rename dialog for any file or folder
- 🗑️ **Move to Recycle Bin** — safe deletion with confirmation; deleted items can always be restored
- 📤 **Share** — share any file or a batch selection to any installed app
- 📁 **Create new folder** — tap ＋ in the top bar → New Folder → name it → done
- 📝 **Create new file** — tap ＋ → New File → type a filename with extension → creates an empty file
- 🗜️ **Zip** — multi-select files/folders → Zip → name your archive → creates a `.zip`
- 📦 **Unzip** — long-press any `.zip` file → "Unzip here" → extracts contents to the same folder

### ✅ Multi-Select & Batch Operations

- Long-press any item to enter selection mode
- Tap additional items to add them to the selection
- "Select All" toggle from the top bar
- Batch **Cut**, **Copy**, **Zip**, **Delete**, and **Share** from the bottom action bar
- Press back to exit selection mode and clear the clipboard

---

## 🗄️ Collections & Navigation Drawer

The hamburger menu (≡) gives you quick access to organized views of your device:

- 💾 **Internal Storage** — browse the full file system with a live storage usage bar (used / total / %)
- 🎵 **Music** — shows only audio files, filtered across all folders
- 🖼️ **Images** — all image files across the device
- 🎬 **Videos** — all video files across the device
- 📷 **Images & Videos** — combined gallery view
- 📄 **PDF** — all PDF documents
- 🤖 **Applications** — installed app browser
- 🗑️ **Recycle Bin** — view and manage deleted items
- ⭐ **Favorites** — your bookmarked files
- ⚙️ **Settings**

---

## 🖼️ Smart Thumbnails

- 🖼️ **Images** — actual image previews loaded via Coil
- 🎬 **Videos** — real video frame thumbnails extracted from video files
- 🎵 **Audio** — music note icon with an orange tint
- 📄 **PDF** — dedicated PDF icon
- 📦 **APK** — Android robot icon
- 🏷️ **All other file types** — colored rounded badge showing the file extension in UPPERCASE (e.g., `DOCX`, `XLSX`, `ZIP`)

---

## 🎬 Media Viewer

- 📖 **Swipeable media viewer** — horizontal pager to swipe through all media in a folder
- 🖼️ **Images** — full-resolution display via Coil
- 🎬 **Videos** — ExoPlayer-based inline video playback with full controls
- 🎵 **Audio** — in-pager audio playback with controls (part of a mixed-media folder)
- 🔁 **Type-isolated swiping** — images and videos swipe together in a loop; audio files stay in their own group
- 👆 **Immersive mode** — tap anywhere on screen to toggle the top bar and system bars; animated slide/fade transitions
- 🔢 **Track counter** — shows current position and total count (e.g., "3 / 12")

---

## 🎥 Video Player

- 🔆 **Brightness gesture** — swipe up/down on the **left** half of the screen to adjust screen brightness
- 🔊 **Volume gesture** — swipe up/down on the **right** half to adjust media volume
- ⏩ **Double-tap seek** — double-tap the left half to rewind 10 seconds; double-tap the right half to fast-forward 10 seconds
- ⚡ **Long-press speed boost** — hold anywhere on the video to play at 1.5× speed; release to return to normal speed
- 💾 **Resume playback** — automatically remembers where you stopped in every video file and resumes from that exact position next time you open it
- 🔒 **Landscape swipe lock** — horizontal swiping between videos is disabled in landscape mode to prevent accidental track changes while watching
- 🎙️ **Background video audio** — optional setting to keep video audio playing when the app goes to background

---

## 🎵 Music Player

- 🎶 **Full-screen music player** — swipeable between all songs in a folder using a horizontal pager
- 🖼️ **Album art** — displays embedded album artwork; falls back to a stylized music note icon when no art is found
- 🎚️ **Seekbar with time labels** — scrub to any position with a live timestamp display
- ▶️ **Playback controls** — Previous, Play/Pause (large highlighted button), Next
- 🔂 **Repeat modes** — toggle between Repeat All and Repeat One; state is preserved across swipes
- 🔔 **Media notification** — persistent notification with play/pause/skip controls powered by Media3 MediaSessionService
- 🏃 **Background playback** — music keeps playing even after closing the app or locking the screen
- 📍 **Resume position** — saves your playback position every ~5 seconds; reopening the player resumes from where you left off
- 📐 **Landscape layout** — album art on the left, controls on the right in landscape orientation
- 🎵 **Track numbering** — shows "2 / 14" style track counter in the top bar

### 🪟 Home-Screen Music Widget

Add the **Music Player** widget to your Android home screen for instant playback control — no need to open the app.

```
╔══════════════════════════════════════════════╗
║  ┌──────┐  Song Title (centered)             ║
║  │Album │  [◀◀]   [ ▶/⏸ ]   [▶▶]   [🔁]   ║
║  │  Art │                                    ║
║  └──────┘                                    ║
╚══════════════════════════════════════════════╝
```

- 🖼️ **Album art** — embedded cover art shown on the left in a compact rounded tile; falls back to a music note icon when none is embedded
- 🏷️ **Track name** — file name displayed centre-aligned next to the art, ellipsised when it overflows
- ⏮️ **Previous** — skip to the previous track in the current folder playlist
- ⏯️ **Play / Pause** — large orange circular button; correctly resumes from the last saved position even when the playback service was not running
- ⏭️ **Next** — skip to the next track
- 🔂 **Repeat toggle** — switches between Repeat All and Repeat One; button tints orange when Repeat One is active
- 🔄 **Live sync** — widget icon and track name update automatically whenever the service changes track, pauses, or resumes — no manual refresh needed
- 📲 **Tap to open** — tapping the album art or track name launches the full Music Player screen for the current track
- 🔌 **Cold-start aware** — pressing Play on the widget while the service is stopped automatically resumes the last played track from the saved position

---

## 📄 PDF Viewer

- 📖 **Dedicated PDF screen** — opens any PDF file in a full-screen reader
- 👆 **Immersive toggle** — tap the screen to show/hide the top bar for distraction-free reading
- 🚫 **No cross-PDF swiping** — PDFs open independently; swiping doesn't jump to other PDFs in the same folder

---

## 🤖 Applications Manager

- 📱 **Browse all installed apps** — grid view of every app on the device with icons and names
- 🔍 **Search apps** — real-time filter by app name
- ⚙️ **Open App Settings** — long-press any app to jump directly to its Android settings page

---

## ⭐ Favorites

- Mark any file or folder as a favorite with a single tap in the bottom sheet or long-press menu
- Toggle favorites in bulk via multi-select mode
- Dedicated **Favorites screen** accessible from the navigation drawer
- Starred items display a ⭐ badge in the file list so you can spot them instantly
- Tap the star in the Favorites screen to remove an item from favorites

---

## 🗑️ Recycle Bin

- Deleted files and folders are safely moved to the Recycle Bin — nothing is lost immediately
- **Restore** any item back to its original location with one tap
- **Permanently delete** individual items or empty the entire bin at once
- Each item shows its original path and the exact date/time it was deleted

---

## 🔐 Security

- 🔢 **6-digit PIN lock** — fully custom in-app numeric keypad shown on app launch
- 🔄 **Shake animation** on wrong PIN entry with an error message
- ⚙️ **Enable / Disable from Settings** — toggle the PIN requirement on or off with a confirmation dialog to prevent accidental lock-outs

---

## ⚙️ Settings

- 🔒 **Master Password** — enable or disable the 6-digit PIN lock screen
- 👁️ **Show Hidden Files** — globally toggle visibility of dot-prefixed files and folders
- 🌗 **Theme** — three-way segmented button: **System** (follows OS), **Light**, **Dark**; applies instantly across the whole app and persists across restarts
- 🎵 **Background Video Playback** — toggle whether video audio continues when the app is minimized

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | **Kotlin** |
| UI Framework | **Jetpack Compose** + **Material 3** |
| Architecture | **ViewModel** + **StateFlow** |
| Media Playback | **Media3 ExoPlayer** + **MediaSessionService** |
| Home Widget | **AppWidgetProvider** + **RemoteViews** |
| Image Loading | **Coil** (images + video frame thumbnails) |
| PDF Rendering | **PdfBox Android** |
| Storage Access | **SAF (Storage Access Framework)** + legacy `java.io.File` |
| Min SDK | **API 24** (Android 7.0) |
| Target SDK | **API 36** |

---

## 📋 Permissions

- `READ_EXTERNAL_STORAGE` / `WRITE_EXTERNAL_STORAGE` — file access on Android 9 and below
- `MANAGE_EXTERNAL_STORAGE` — full storage access on Android 11+
- `FOREGROUND_SERVICE` — music background playback notification
- `POST_NOTIFICATIONS` — media playback notification on Android 13+

---

<div align="center">

Built with ❤️ using Jetpack Compose · Material You · ExoPlayer · AppWidgetProvider

</div>
