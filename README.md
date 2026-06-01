# Files App — Feature Tracker

## File Explorer (Core)

| #  | Feature                                                                                               | Status | Notes                                                                                                                                                                                                              |
|----|-------------------------------------------------------------------------------------------------------|--------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | Navigate into all folders: Tap folder to enter; back button to go up                                  | ✅ Done | Uses Storage Access Framework (SAF) for Android 11+; legacy File API for older versions                                                                                                                            |
| 2  | Show full path from root (breadcrumb)                                                                 | ✅ Done | Scrollable breadcrumb bar; tap any segment to jump; collapses `/storage/emulated/0` to "Internal Storage"                                                                                                          |
| 3  | Show/Hide hidden files toggle                                                                         | ✅ Done | Eye icon in top bar toggles `.` prefixed files/folders                                                                                                                                                             |
| 4  | Show full filenames with extensions                                                                   | ✅ Done | Full name displayed with up to 2-line wrap                                                                                                                                                                         |
| 5  | Cut / Copy / Paste                                                                                    | ✅ Done | Long-press → bottom sheet; FAB appears for paste                                                                                                                                                                   |
| 6  | Rename files/folders                                                                                  | ✅ Done | Long-press → Rename → dialog with editable name                                                                                                                                                                    |
| 7  | Delete files/folders                                                                                  | ✅ Done | Long-press → Delete → confirmation dialog                                                                                                                                                                          |
| 8  | Storage permission handling                                                                           | ✅ Done | MANAGE_EXTERNAL_STORAGE for Android 11+; legacy for older                                                                                                                                                          |
| 9  | File metadata display (size, date)                                                                    | ✅ Done | Size for files, item count for folders, last modified date                                                                                                                                                         |
| 10 | Create new folder                                                                                     | ✅ Done | "+" menu in top bar → New Folder → name dialog → creates folder                                                                                                                                                    |
| 11 | Create new file                                                                                       | ✅ Done | "+" menu in top bar → New File → name dialog → creates empty file                                                                                                                                                  |
| 12 | Pull to refresh in each folder                                                                        | ✅ Done | PullToRefreshBox wraps file list; refresh() reloads current directory                                                                                                                                              |
| 13 | Preview thumbnails for files whenever possible                                                        | ✅ Done | Coil-based: image thumbnails, video frame thumbnails, typed icons for PDF/audio/docs/APK                                                                                                                           |
| 14 | Thumbnails for non-picture file types with CAPITAL letters of file extensions                         | ✅ Done | Colored rounded box with extension in UPPERCASE                                                                                                                                                                    |
| 15 | Distinguish hidden files with slight 50% opacity                                                      | ✅ Done | Files/folders starting with `.` render at 50% alpha                                                                                                                                                                |
| 16 | Recent Files screen with search filter                                                                | ✅ Done | Recursive scan; sorted by date/size/type/path; inline search; filter by file type (Images, Videos, Audio, PDF)                                                                                                     |
| 17 | Display folder size below folder name                                                                 | ✅ Done | Async recursive size calculation; shows "X items • size" or "Calculating…" while computing                                                                                                                         |
| 18 | Recent files screen MUST have all these Sort options name, size, date, type and sort by PATH          | ✅ Done | Dropdown sort menu in top bar; supports Name, Size, Date, Type, Path; tap same option toggles asc/desc; integrated with file type filtering                                                                        |
| 19 | Multi-select files for batch operations                                                               | ✅ Done | Long-press → "Select" enters selection mode; batch Delete / Cut / Copy / Zip via bottom bar; "All" selects everything; ✕ exits                                                                                     |
| 20 | Zip/Unzip support                                                                                     | ✅ Done | Multi-select → Zip icon → enter name dialog → creates .zip; long-press .zip file → "Unzip here" extracts to same folder                                                                                            |
| 21 | Favorite files section in Tile activity and support to change favorite status for each and every file | ✅ Done | Long-press any file → "Add to Favorites" / "Remove from Favorites" in bottom sheet; star badge on favorited items in explorer; Favorites screen accessible from the ⋮ menu; tap star in Favorites screen to remove |
| 22 | Any file can be shared onto suitable phone apps                                                       | ✅ Done |                                                                                                                                                                                                                    | 
| 23 | Recycle Bin for deleted files                                                                         | ✅ Done | Dedicated Recycle Bin screen; accessible from Home tiles; supports restoration and permanent deletion                                                                                                              |

## Media Viewer (Swipeable)

| # | Feature                                                                                                                           | Status | Notes                                                                                           |
|---|-----------------------------------------------------------------------------------------------------------------------------------|--------|-------------------------------------------------------------------------------------------------|
| 1 | Media Viewer: swipeable image/video/audio playback for all media in a device group by folder                                      | ✅ Done | HorizontalPager with ExoPlayer; images display, videos play with controls, audio plays with UI  |
| 2 | Type-isolated swiping: images+videos grouped together, audio separate; loop swiping for visual media                              | ✅ Done | Auto-detects file type; images & videos swipe together with infinite loop; audio stays isolated |
| 4 | Open single PDF only: no horizontal swipe through all PDF files in same folder. PDF view screen needed as toggling immersive view | ✅ Done |                                                                                                 |
| 5 | Toggle immersive view activity must allow users to toggle complete immersive view if touched on screen                            | ✅ Done | Tap toggles top bar + system bars; animated slide/fade; restores on exit                        |

## Photos / Gallery features

| # | Feature                                          | Status  | Notes |
|---|--------------------------------------------------|---------|-------|
| 2 | Pinch to zoom and pan for images                 | Pending |       |
| 3 | Basic image editing (Crop, Rotate, Flip)         | Pending |       |
| 4 | Set image as wallpaper                           | Pending |       |
| 5 | Slideshow mode with configurable intervals       | Pending |       |
| 6 | View EXIF metadata (Location, Camera specs, ISO) | Pending |       |
| 7 | Group photos by Date (Today, Yesterday, Month)   | Pending |       |

## Music Player and Video Player

| #  | Feature                                                                                                                   | Status  | Notes                                                                                                                                                         |
|----|---------------------------------------------------------------------------------------------------------------------------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 2  | Music play in background even after app is closed                                                                         | ✅ Done  | Service keeps playing on dispose; state hoisting ensures UI syncs on return                                                                                   |
| 3  | Music play screen: add toggle controls like repeat current song and seek bar                                              | ✅ Done  |                                                                                                                                                               |
| 5  | Music play screen: add play/pause, next, and previous buttons                                                             | ✅ Done  | Single horizontal control row: circular Repeat (64dp), Previous (64dp), Play/Pause (76dp orange), Next (64dp); uses MediaController.seekToNext/seekToPrevious |
| 6  | Music play notification with playback controls                                                                            | ✅ Done  | Media3 MediaSessionService with auto notification; play/pause/seek controls                                                                                   |
| 7  | Music play screen: show song art image with images' width wrap left right and height wrap content                         | ✅ Done  |                                                                                                                                                               |
| 8  | If no song art image then show default music icon with same width and height as mentioned above                           | ✅ Done  |                                                                                                                                                               |
| 2  | Video playback: Gesture controls for brightness and volume, which will be show/hidden the same way as play pause controls only | Done    |                                                                                                                                                               |
| 3  | Video Playback: Double tap 10 seconds for forward/rewind . Screen's left/right side double tap decides this behaviour     | done    |                                                                                                                                                               |
| 5  | Video: Subtitle support (SRT, VTT) and audio track selection                                                              | Pending |                                                                                                                                                               |
| 6  | Video: Long press while video Playback to increase playback speed to 1.5x                                                 | done    |                                                                                                                                                               |
| 7  | Video: Picture-in-Picture (PiP) mode                                                                                      | Pending |                                                                                                                                                               |
| 8  | Video: Screen lock to prevent accidental touches                                                                          | Pending |                                                                                                                                                               |
| 9  | Video: Add toggle control small button while playing video to support 'videos Background playback                         | done    |                                                                                                                                                               |
| 11 | Video: Long press to play video in 1.5x speed; release to restore speed                                                   | done    |                                                                                                                                                               |
| 12 | Video: Double tap left/right half of screen to rewind/forward 10 seconds                                                  | done    |                                                                                                                                                               |
| 13 | Video: Remember video files' playback position. everytime i am opening video it MUST resume from last played duration.    | pending |                                                                                                                                                               |

## Applications

| #  | Feature                                                 | Status  | Notes |
|----|---------------------------------------------------------|---------|-------|
| 1  | Applications screen: 7x7 grid, single-line names        | Pending |       |
| 2  | List installed user apps and system apps (toggle)       | Pending |       |
| 3  | Extract APK from installed apps (App Backup)            | Pending |       |
| 4  | Uninstall apps directly from the list                   | Pending |       |
| 5  | Open app on tap; system settings via long-press         | Pending |       |
| 6  | Share APK file via Bluetooth/Apps                       | Pending |       |
| 7  | Hidden apps section (user apps only)                    | Pending |       |
| 8  | Search bar filter at top                                | Pending |       |

## Security & Authentication

| # | Feature                                                                                                 | Status  | Notes                                                  |
|---|---------------------------------------------------------------------------------------------------------|---------|--------------------------------------------------------|
| 1 | PIN lock screen with custom in-app numeric keypad                                                       | ✅ Done  |                                                        |
| 2 | Add 'Ask for master password' flag to bypass password entering activity when master password is not set | ✅ Done  | Integrated with SettingsManager and MainActivity check |
| 3 | Fingerprint / Biometric lock for private folders                                                        | Pending |                                                        |

## Settings & System

| # | Feature                                                                                                                                 | Status  | Notes                                                                                                                                 |
|---|-----------------------------------------------------------------------------------------------------------------------------------------|---------|---------------------------------------------------------------------------------------------------------------------------------------|
| 1 | Home screen with category cards                                                                                                         | ✅ Done  | Replace into settings page later                                                                                                      |
| 2 | Dark/Light theme toggle                                                                                                                 | ✅ Done  | Three-way segmented button in Settings: System / Light / Dark; persisted to SharedPreferences; applies instantly across the whole app |
| 3 | Settings screen                                                                                                                         | ✅ Done  | Accessible from Home; manages master password toggle                                                                                  |
| 4 | Camera button directly from app                                                                                                         | Pending |                                                                                                                                       |
| 5 | Cloud storage integration (Google Drive and Mega) . Local internal storage to Google Drive storage file cut copy paste MUST BE POSSIBLE | Pending |                                                                                                                                       |

## Issues observed
| #  | Issue                                                                                | Status        | Resolution                                                                                  |
|----|--------------------------------------------------------------------------------------|---------------|---------------------------------------------------------------------------------------------|
| 2  | Media player resets position on rotation                                             | ✅ Fixed       | MediaViewerActivity already has `android:configChanges`; activity not recreated on rotation |