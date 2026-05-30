# Files App — Feature Tracker

## File Explorer (Core)

| #  | Feature                                                                       | Status  | Notes                                                                                                     |
|----|-------------------------------------------------------------------------------|---------|-----------------------------------------------------------------------------------------------------------|
| 1  | Navigate into all folders: Tap folder to enter; back button to go up          | ✅ Done  | Uses Storage Access Framework (SAF) for Android 11+; legacy File API for older versions                   |
| 2  | Show full path from root (breadcrumb)                                         | ✅ Done  | Scrollable breadcrumb bar; tap any segment to jump; collapses `/storage/emulated/0` to "Internal Storage" |
| 3  | Show/Hide hidden files toggle                                                 | ✅ Done  | Eye icon in top bar toggles `.` prefixed files/folders                                                    |
| 4  | Show full filenames with extensions                                           | ✅ Done  | Full name displayed with up to 2-line wrap                                                                |
| 5  | Cut / Copy / Paste                                                            | ✅ Done  | Long-press → bottom sheet; FAB appears for paste                                                          |
| 6  | Rename files/folders                                                          | ✅ Done  | Long-press → Rename → dialog with editable name                                                           |
| 7  | Delete files/folders                                                          | ✅ Done  | Long-press → Delete → confirmation dialog                                                                 |
| 8  | Storage permission handling                                                   | ✅ Done  | MANAGE_EXTERNAL_STORAGE for Android 11+; legacy for older                                                 |
| 9  | File metadata display (size, date)                                            | ✅ Done  | Size for files, item count for folders, last modified date                                                |
| 10 | Create new folder                                                             | ✅ Done  | "+" menu in top bar → New Folder → name dialog → creates folder                                           |
| 11 | Create new file                                                               | ✅ Done  | "+" menu in top bar → New File → name dialog → creates empty file                                         |
| 12 | Pull to refresh in each folder                                                | ✅ Done  | PullToRefreshBox wraps file list; refresh() reloads current directory                                     |
| 13 | Preview thumbnails for files whenever possible                                | ✅ Done  | Coil-based: image thumbnails, video frame thumbnails, typed icons for PDF/audio/docs/APK                  |
| 14 | Thumbnails for non-picture file types with CAPITAL letters of file extensions | ✅ Done  | Colored rounded box with extension in UPPERCASE                                                           |
| 15 | Distinguish hidden files with slight 50% opacity                              | ✅ Done  | Files/folders starting with `.` render at 50% alpha                                                       |
| 16 | Recent Files screen with search filter                                        | ✅ Done  | Recursive scan of storage; sorted by date descending; inline search filter by file name                   |
| 17 | Display folder size below folder name                                         | Pending |                                                                                                           |
| 18 | Sort options (name, size, date, type)                                         | Pending |                                                                                                           |
| 19 | Multi-select files for batch operations                                       | Pending |                                                                                                           |
| 20 | Zip/Unzip support                                                             | Pending |                                                                                                           |
| 21 | Bookmarks / Favorites                                                         | Pending |                                                                                                           |
| 22 | Storage usage overview                                                        | Pending |                                                                                                           |

## Media Viewer (Swipeable)

| #  | Feature                                                                                                | Status  | Notes                                                                                          |
|----|--------------------------------------------------------------------------------------------------------|---------|------------------------------------------------------------------------------------------------|
| 1  | Media Viewer: swipeable image/video/audio playback for all media in a folder                           | ✅ Done  | HorizontalPager with ExoPlayer; images display, videos play with controls, audio plays with UI |
| 2  | PDF opens along with images, videos, audio while swiping left and right in existing flow               | Pending |                                                                                                |
| 3  | Sort mode (name, date, size, type) in media viewer                                                     | Pending |                                                                                                |
| 4  | Toggle immersive view activity must allow users to toggle complete immersive view if touched on screen | ✅ Done  | Tap toggles top bar + system bars; animated slide/fade; restores on exit                       |

## Photos / Gallery

| #  | Feature                                              | Status  | Notes |
|----|------------------------------------------------------|---------|-------|
| 1  | Photos screen (group by folder)                      | Pending |       |
| 2  | Pinch to zoom and pan for images                     | Pending |       |
| 3  | Basic image editing (Crop, Rotate, Flip)             | Pending |       |
| 4  | Set image as wallpaper or contact photo              | Pending |       |
| 5  | Slideshow mode with configurable intervals           | Pending |       |
| 6  | View EXIF metadata (Location, Camera specs, ISO)     | Pending |       |
| 7  | Group photos by Date (Today, Yesterday, Month)       | Pending |       |

## Music Player

| #  | Feature                                                                                                   | Status  | Notes                                                                       |
|----|-----------------------------------------------------------------------------------------------------------|---------|-----------------------------------------------------------------------------|
| 1  | Music screen (group by folder)                                                                            | Pending |                                                                             |
| 2  | Music play in background even after app is closed                                                         | ✅ Done  | Service keeps playing on dispose; state hoisting ensures UI syncs on return |
| 3  | Music play screen: add controls like repeat song, repeat entire folder audio files, shuffle, and seek bar | Pending |                                                                             |
| 4  | Music play screen: show song title, artist name, and album name                                           | Pending |                                                                             |
| 5  | Music play screen: add play/pause, next, and previous buttons                                             | Pending |                                                                             |
| 6  | Music play notification with playback controls                                                            | ✅ Done  | Media3 MediaSessionService with auto notification; play/pause/seek controls |
| 7  | Create and manage playlists                                                                               | Pending |                                                                             |
| 8  | Audio visualizer on playback screen                                                                       | Pending |                                                                             |
| 9  | Sleep timer for music playback                                                                            | Pending |                                                                             |
| 10 | Equalizer and bass boost settings                                                                         | Pending |                                                                             |
| 11 | Fetch and display lyrics (LRC files or embedded)                                                          | Pending |                                                                             |
| 12 | Shortcut to launch music player from TILES screen                                                         | Pending |                                                                             |

## Video Player (Existing video player controls are not as good as they are supposed to be)

| #  | Feature                                                                  | Status  | Notes |
|----|--------------------------------------------------------------------------|---------|-------|
| 1  | Videos screen (group by folder)                                          | Pending |       |
| 2  | Gesture controls for brightness and volume                               | Pending |       |
| 3  | Double tap to seek forward/backward                                      | Pending |       |
| 4  | Aspect ratio toggle (Fit, Fill, Stretch, 16:9, 4:3)                      | Pending |       |
| 5  | Subtitle support (SRT, VTT) and audio track selection                    | Pending |       |
| 6  | Playback speed control (0.5x to 2.0x)                                    | Pending |       |
| 7  | Picture-in-Picture (PiP) mode                                            | Pending |       |
| 8  | Screen lock to prevent accidental touches                                | Pending |       |
| 9  | Background play (audio only) toggle                                      | Pending |       |
| 10 | Enable/disable left/right swiping between all video files in that folder | Pending |       | 

## Documents

| #  | Feature                                              | Status  | Notes |
|----|------------------------------------------------------|---------|-------|
| 1  | Documents screen (group by folder)                   | Pending |       |
| 2  | In-app PDF viewer with zoom and page navigation      | Pending |       |
| 3  | Text file editor (Open, Edit, Save .txt, .log, .md)  | Pending |       |
| 4  | Document search by content (OCR or text indexing)    | Pending |       |
| 5  | Print document via Google Cloud Print / System Print | Pending |       |

## Applications

| #  | Feature                                                 | Status  | Notes |
|----|---------------------------------------------------------|---------|-------|
| 1  | Applications screen                                     | Pending |       |
| 2  | List installed user apps and system apps (toggle)       | Pending |       |
| 3  | Extract APK from installed apps (App Backup)            | Pending |       |
| 4  | Uninstall apps directly from the list                   | Pending |       |
| 5  | Open app info / system settings for selected app        | Pending |       |
| 6  | Share APK file via Bluetooth/Apps                       | Pending |       |

## Security & Authentication

| # | Feature                                                                                                 | Status  | Notes                                                  |
|---|---------------------------------------------------------------------------------------------------------|---------|--------------------------------------------------------|
| 1 | PIN lock screen with custom in-app numeric keypad                                                       | ✅ Done  |                                                        |
| 2 | Add 'Ask for master password' flag to bypass password entering activity when master password is not set | ✅ Done  | Integrated with SettingsManager and MainActivity check |
| 3 | Fingerprint / Biometric lock for private folders                                                        | Pending |                                                        |

## Settings & System

| # | Feature                                              | Status  | Notes                                                |
|---|------------------------------------------------------|---------|------------------------------------------------------|
| 1 | Home screen with category cards                      | ✅ Done  | Replace into settings page later                     |
| 2 | Dark/Light theme toggle                              | Pending | Currently follows system theme                       |
| 3 | Settings screen                                      | ✅ Done  | Accessible from Home; manages master password toggle |
| 4 | Camera button directly from app                      | Pending |                                                      |
| 5 | Cloud storage integration (Google Drive, Dropbox)    | Pending |                                                      |
| 6 | Video background play flag/toggle in settings screen | Pending |                                                      |

## Issues observed
1. On any screen if screen is rotated, then app is launching password entering screen. 
This is because of the fact that on rotation activity is recreated and in onCreate() method we are
checking for password and launching password entering screen if password is set. To fix this issue, 
we can check `savedInstanceState == null` in `onCreate()` to ensure the password check only triggers on a fresh launch.
we can add a flag to check if the activity is being recreated due to rotation and skip the password 
check in that case.
2. Music playback and video playback on different streams it seems. Due to this issue
3. Music player skipping tracks. There is some issue like file name displayed for music play is not the audio file which is playing 