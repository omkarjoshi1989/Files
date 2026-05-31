# Files App — Feature Tracker

## File Explorer (Core)

| #  | Feature                                                                                               | Status  | Notes                                                                                                                                                                                                              |
|----|-------------------------------------------------------------------------------------------------------|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | Navigate into all folders: Tap folder to enter; back button to go up                                  | ✅ Done  | Uses Storage Access Framework (SAF) for Android 11+; legacy File API for older versions                                                                                                                            |
| 2  | Show full path from root (breadcrumb)                                                                 | ✅ Done  | Scrollable breadcrumb bar; tap any segment to jump; collapses `/storage/emulated/0` to "Internal Storage"                                                                                                          |
| 3  | Show/Hide hidden files toggle                                                                         | ✅ Done  | Eye icon in top bar toggles `.` prefixed files/folders                                                                                                                                                             |
| 4  | Show full filenames with extensions                                                                   | ✅ Done  | Full name displayed with up to 2-line wrap                                                                                                                                                                         |
| 5  | Cut / Copy / Paste                                                                                    | ✅ Done  | Long-press → bottom sheet; FAB appears for paste                                                                                                                                                                   |
| 6  | Rename files/folders                                                                                  | ✅ Done  | Long-press → Rename → dialog with editable name                                                                                                                                                                    |
| 7  | Delete files/folders                                                                                  | ✅ Done  | Long-press → Delete → confirmation dialog                                                                                                                                                                          |
| 8  | Storage permission handling                                                                           | ✅ Done  | MANAGE_EXTERNAL_STORAGE for Android 11+; legacy for older                                                                                                                                                          |
| 9  | File metadata display (size, date)                                                                    | ✅ Done  | Size for files, item count for folders, last modified date                                                                                                                                                         |
| 10 | Create new folder                                                                                     | ✅ Done  | "+" menu in top bar → New Folder → name dialog → creates folder                                                                                                                                                    |
| 11 | Create new file                                                                                       | ✅ Done  | "+" menu in top bar → New File → name dialog → creates empty file                                                                                                                                                  |
| 12 | Pull to refresh in each folder                                                                        | ✅ Done  | PullToRefreshBox wraps file list; refresh() reloads current directory                                                                                                                                              |
| 13 | Preview thumbnails for files whenever possible                                                        | ✅ Done  | Coil-based: image thumbnails, video frame thumbnails, typed icons for PDF/audio/docs/APK                                                                                                                           |
| 14 | Thumbnails for non-picture file types with CAPITAL letters of file extensions                         | ✅ Done  | Colored rounded box with extension in UPPERCASE                                                                                                                                                                    |
| 15 | Distinguish hidden files with slight 50% opacity                                                      | ✅ Done  | Files/folders starting with `.` render at 50% alpha                                                                                                                                                                |
| 16 | Recent Files screen with search filter                                                                | ✅ Done  | Recursive scan; sorted by date/size/type/path; inline search; filter by file type (Images, Videos, Audio, PDF)                                                                                                     |
| 17 | Display folder size below folder name                                                                 | ✅ Done  | Async recursive size calculation; shows "X items • size" or "Calculating…" while computing                                                                                                                         |
| 18 | Recent files screen MUST have all these Sort options name, size, date, type and sort by PATH          | ✅ Done  | Dropdown sort menu in top bar; supports Name, Size, Date, Type, Path; tap same option toggles asc/desc; integrated with file type filtering                                                                        |
| 19 | Multi-select files for batch operations                                                               | ✅ Done  | Long-press → "Select" in bottom sheet enters selection mode; batch Delete / Cut / Copy / Zip via bottom bar; "All" selects everything; ✕ exits                                                                     |
| 20 | Zip/Unzip support                                                                                     | ✅ Done  | Multi-select → Zip icon → enter name dialog → creates .zip; long-press .zip file → "Unzip here" extracts to same folder                                                                                            |
| 21 | Favorite files section in Tile activity and support to change favorite status for each and every file | ✅ Done  | Long-press any file → "Add to Favorites" / "Remove from Favorites" in bottom sheet; star badge on favorited items in explorer; Favorites screen accessible from the ⋮ menu; tap star in Favorites screen to remove |
| 22 | Any file can be shared onto suitable phone apps                                                       | Pending |                                                                                                                                                                                                                    | 

## Media Viewer (Swipeable)

| # | Feature                                                                                                | Status  | Notes                                                                                           |
|---|--------------------------------------------------------------------------------------------------------|---------|-------------------------------------------------------------------------------------------------|
| 1 | Media Viewer: swipeable image/video/audio playback for all media in a device group by folder           | ✅ Done  | HorizontalPager with ExoPlayer; images display, videos play with controls, audio plays with UI  |
| 2 | Type-isolated swiping: images+videos grouped together, audio separate; loop swiping for visual media   | ✅ Done  | Auto-detects file type; images & videos swipe together with infinite loop; audio stays isolated |
| 3 | PDF opens within app only . no external PDF file viewer.                                               | Pending | Deferred to future — requires in-app PDF viewer implementation first (see Documents #2)         |
| 4 | Open single PDF only: no horizontal swipe through all PDF files in same folder                         | pending | Implement via `EXTRA_SINGLE_FILE_MODE` in `MediaViewerActivity`                                 |
| 5 | Toggle immersive view activity must allow users to toggle complete immersive view if touched on screen | ✅ Done  | Tap toggles top bar + system bars; animated slide/fade; restores on exit                        |

## Photos / Gallery features

| # | Feature                                          | Status  | Notes |
|---|--------------------------------------------------|---------|-------|
| 2 | Pinch to zoom and pan for images                 | Pending |       |
| 3 | Basic image editing (Crop, Rotate, Flip)         | Pending |       |
| 4 | Set image as wallpaper                           | Pending |       |
| 5 | Slideshow mode with configurable intervals       | Pending |       |
| 6 | View EXIF metadata (Location, Camera specs, ISO) | Pending |       |
| 7 | Group photos by Date (Today, Yesterday, Month)   | Pending |       |

## Music Player

| # | Feature                                                                                                   | Status  | Notes                                                                       |
|---|-----------------------------------------------------------------------------------------------------------|---------|-----------------------------------------------------------------------------|
| 1 | All music files in device group by folder. This MUST be the shotcut from settings menu durectly           | Pending |                                                                             |
| 2 | Music play in background even after app is closed                                                         | ✅ Done  | Service keeps playing on dispose; state hoisting ensures UI syncs on return |
| 3 | Music play screen: add controls like repeat song, repeat entire folder audio files, shuffle, and seek bar | Pending |                                                                             |
| 4 | Music play screen: show song title, artist name, and album name                                           | Pending |                                                                             |
| 5 | Music play screen: add play/pause, next, and previous buttons                                             | Pending |                                                                             |
| 6 | Music play notification with playback controls                                                            | ✅ Done  | Media3 MediaSessionService with auto notification; play/pause/seek controls |
| 7 | Music play screen: show song art image with images' width wrap left right and height wrap content         | Pending |                                                                             |
| 8 | If no song art image then show default music icon with same width and height as mentioned above           | Pending |                                                                             |

## Video Player

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
| 11 | Long press to play video in 1.5x speed; release to restore speed         | Pending |       |
| 12 | Double tap left/right half of screen to rewind/forward 10 seconds        | Pending |       |

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

| # | Feature                                              | Status  | Notes                                                                                                                                 |
|---|------------------------------------------------------|---------|---------------------------------------------------------------------------------------------------------------------------------------|
| 1 | Home screen with category cards                      | ✅ Done  | Replace into settings page later                                                                                                      |
| 2 | Dark/Light theme toggle                              | ✅ Done  | Three-way segmented button in Settings: System / Light / Dark; persisted to SharedPreferences; applies instantly across the whole app |
| 3 | Settings screen                                      | ✅ Done  | Accessible from Home; manages master password toggle                                                                                  |
| 4 | Camera button directly from app                      | Pending |                                                                                                                                       |
| 5 | Cloud storage integration (Google Drive, Dropbox)    | Pending |                                                                                                                                       |
| 6 | Video background play flag/toggle in settings screen | Pending |     

## uncategorized features
Camera button directly from app

## Issues observed
| #  | Issue                                                                                | Status        | Resolution                                                                                  |
|----|--------------------------------------------------------------------------------------|---------------|---------------------------------------------------------------------------------------------|
| 2  | Media player resets position on rotation                                             | ✅ Fixed       | MediaViewerActivity already has `android:configChanges`; activity not recreated on rotation |
| 5  | UI doesn't update immediately when files are deleted via external apps               | ✅ Fixed       | FileObserver watches current directory; ON_RESUME refresh as safety net                     |
| 6  | Lag or OOM errors when scrolling folders with 1000+ images                           | need to check |                                                                                             |
| 7  | Long-running file operations (Copy/Move) cancel if app is backgrounded               | Pending       |                                                                                             |
| 8  | Search query and results are lost on screen rotation                                 | ✅ Fixed       | State lives in ViewModel (survives rotation); only transient UI state (dialogs) is local    |
| 9  | recent files is not showing hidden files as 50% opacity                              | ✅ Fixed       | Added `.alpha(0.5f)` for hidden files in RecentFileItem                                     |
| 10 | video playback MUST keep screen ON during playback; conflict with system sleep timer | Pending       |                                                                                             |
