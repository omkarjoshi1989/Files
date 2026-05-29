# Files App — Feature Tracker

## v1.0 — Initial Build

Moving ahead treat and consider README.md file as FEATURES.md file only. there is not need of seperate FEATURES and README md files now onwards. 

## Implemented Features

| #  | Feature                                                                       | Status | Notes                                                                                                     |
|----|-------------------------------------------------------------------------------|--------|-----------------------------------------------------------------------------------------------------------|
| 1  | Navigate into all folders : Tap folder to enter; back button to go up         | ✅ Done | Uses Storage Access Framework (SAF) for Android 11+; legacy File API for older versions                   |
| 2  | Show full path from root (breadcrumb)                                         | ✅ Done | Scrollable breadcrumb bar; tap any segment to jump; collapses `/storage/emulated/0` to "Internal Storage" |
| 3  | Show/Hide hidden files toggle                                                 | ✅ Done | Eye icon in top bar toggles `.` prefixed files/folders                                                    |
| 4  | Show full filenames with extensions                                           | ✅ Done | Full name displayed with up to 2-line wrap                                                                |
| 5  | Cut / Copy / Paste                                                            | ✅ Done | Long-press → bottom sheet; FAB appears for paste                                                          |
| 6  | Rename files/folders                                                          | ✅ Done | Long-press → Rename → dialog with editable name                                                           |
| 7  | Delete files/folders                                                          | ✅ Done | Long-press → Delete → confirmation dialog                                                                 |
| 9  | Storage permission handling                                                   | ✅ Done | MANAGE_EXTERNAL_STORAGE for Android 11+; legacy for older                                                 |
| 10 | File metadata display (size, date)                                            | ✅ Done | Size for files, item count for folders, last modified date                                                |
| 11 | Create new folder                                                             | ✅ Done | "+" menu in top bar → New Folder → name dialog → creates folder                                           |
| 12 | Create new file                                                               | ✅ Done | "+" menu in top bar → New File → name dialog → creates empty file                                         |
| 21 | Pull to refresh in each folder                                                | ✅ Done | PullToRefreshBox wraps file list; refresh() reloads current directory                                     |
| 24 | Preview thumbnails for files whenever possible                                | ✅ Done | Coil-based: image thumbnails, video frame thumbnails, typed icons for PDF/audio/docs/APK                  |
| 25 | Thumbnails for non-picture file types with CAPITAL letters of file extensions | ✅ Done | Colored rounded box with extension in UPPERCASE                                                           |
| 26 | Distinguish hidden files with slight 50% opacity                              | ✅ Done | Files/folders starting with `.` render at 50% alpha                                                       |
| 27 | Recent Files screen with search filter                                        | ✅ Done | Recursive scan of storage; sorted by date descending; inline search filter by file name                   |
| 28 | Media Viewer: swipeable image/video/audio playback for all media in a folder  | ✅ Done | HorizontalPager with ExoPlayer; images display, videos play with controls, audio plays with UI            |

## Pending Features

| #  | Feature                                                                                                 | Status  | Notes |
|----|---------------------------------------------------------------------------------------------------------|---------|-------|
| 14 | Sort options (name, size, date, type)                                                                   | Pending |       |
| 14 | Fingerprint / Biometric lock for private folders                                                        | Pending |       |
| 15 | Multi-select files for batch operations                                                                 | Pending |       |
| 16 | Zip/Unzip support                                                                                       | Pending |       |
| 17 | Bookmarks / Favorites                                                                                   | Pending |       |
| 18 | Storage usage overview                                                                                  | Pending |       |
| 19 | Dark/Light theme toggle (Currently follows system theme)                                                | Pending |       |
| 20 | Settings screen                                                                                         | Pending |       |
| 22 | Home screen with category cards (pending : replace this entire screen into settings page later)         | Pending |       |
| 22 | Photos screen (group by folder)                                                                         | Pending |       |
| 22 | Music screen (group by folder)                                                                          | Pending |       |
| 22 | Videos screen (group by folder)                                                                         | Pending |       |
| 22 | Documents screen (group by folder)                                                                      | Pending |       |
| 22 | Applications screen                                                                                     | Pending |       |
| 29 | Sort mode (name, date, size, type) in media viewer                                                      | Pending |       |
| 30 | Display Folder size below folder name                                                                   | Pending |       |
| 31 | Add 'Ask for master password' flag to bypass password entering activity when master password is not set | Pending |       |
| 32 | Camera button directly from app                                                                         | Pending |       |
| 33 | PDF opens along with images, videos, audio while swiping left and right in existing flow                | Pending |       |

## Pending features is to design complete new Music Player from scratch and give shortcut to launch this music player from TILES screen
| #  | Feature                                                                 | Status  | Notes |
|----|-------------------------------------------------------------------------|---------|-------|
| 34 | Music play complete background even after app is closed                 | Pending |       |
| 35 | Music play screen: add controls like repeat song, shuffle, and seek bar | Pending |       |
| 36 | Music play screen: show song title, artist name, and album name         | Pending |       |
| 37 | Music play screen: add play/pause, next, and previous buttons           | Pending |       |
| 38 | Music play notification with playback controls                          | Pending |       |
| 39 | Create and manage playlists                                             | Pending |       |
| 40 | Audio visualizer on playback screen                                     | Pending |       |
| 41 | Sleep timer for music playback                                          | Pending |       |
| 42 | Equalizer and bass boost settings                                       | Pending |       |
| 43 | Fetch and display lyrics (LRC files or embedded)                        | Pending |       |

## Pending video player screen must have following features
| #   | Feature                                                                 | Status  | Notes |
|-----|-------------------------------------------------------------------------|---------|-------|
| 44  | Gesture controls for brightness and volume                              | Pending |       |
| 45  | Double tap to seek forward/backward                                     | Pending |       |
| 46  | Aspect ratio toggle (Fit, Fill, Stretch, 16:9, 4:3)                     | Pending |       |
| 47  | Subtitle support (SRT, VTT) and audio track selection                   | Pending |       |
| 48  | Playback speed control (0.5x to 2.0x)                                   | Pending |       |
| 49  | Picture-in-Picture (PiP) mode                                           | Pending |       |
| 50  | Screen lock to prevent accidental touches                               | Pending |       |
| 51  | Background play (audio only) toggle                                     | Pending |       |

## Pending Photos/Gallery screen must have following features
| #   | Feature                                                                 | Status  | Notes |
|-----|-------------------------------------------------------------------------|---------|-------|
| 52  | Pinch to zoom and pan for images                                        | Pending |       |
| 53  | Basic image editing (Crop, Rotate, Flip)                                | Pending |       |
| 54  | Set image as wallpaper or contact photo                                 | Pending |       |
| 55  | Slideshow mode with configurable intervals                              | Pending |       |
| 56  | View EXIF metadata (Location, Camera specs, ISO)                        | Pending |       |
| 57  | Group photos by Date (Today, Yesterday, Month)                          | Pending |       |

## Pending Documents screen must have following features
| #   | Feature                                                                 | Status  | Notes |
|-----|-------------------------------------------------------------------------|---------|-------|
| 58  | In-app PDF viewer with zoom and page navigation                         | Pending |       |
| 59  | Text file editor (Open, Edit, Save .txt, .log, .md)                     | Pending |       |
| 60  | Document search by content (OCR or text indexing)                       | Pending |       |
| 61  | Print document via Google Cloud Print / System Print                    | Pending |       |

## Pending Applications screen must have following features
| #   | Feature                                                                 | Status  | Notes |
|-----|-------------------------------------------------------------------------|---------|-------|
| 62  | List installed user apps and system apps (toggle)                       | Pending |       |
| 63  | Extract APK from installed apps (App Backup)                            | Pending |       |
| 64  | Uninstall apps directly from the list                                   | Pending |       |
| 65  | Open app info / system settings for selected app                        | Pending |       |
| 66  | Share APK file via Bluetooth/Apps                                       | Pending |       |

## Pending Settings & System features
| #  | Feature                                                                 | Status  | Notes |
|----|-------------------------------------------------------------------------|---------|-------|
| 69 | Cloud storage integration (Google Drive, Dropbox)                       | Pending |       |


*Add new feature requests below the Pending table. Update status when implemented.*
