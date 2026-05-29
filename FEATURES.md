# Files App — Feature Tracker

## v1.0 — Initial Build

| # | Feature | Status | Notes |
|---|---------|--------|-------|
| 1 | Navigate into all folders | ✅ Done | Tap folder to enter; back button to go up |
| 2 | Show full path from root (breadcrumb) | ✅ Done | Scrollable breadcrumb bar; tap any segment to jump |
| 3 | Show/Hide hidden files toggle | ✅ Done | Eye icon in top bar toggles `.` prefixed files/folders |
| 4 | Show full filenames with extensions | ✅ Done | Full name displayed with up to 2-line wrap |
| 5 | Cut / Copy / Paste | ✅ Done | Long-press → bottom sheet; FAB appears for paste |
| 6 | Rename files/folders | ✅ Done | Long-press → Rename → dialog with editable name |
| 7 | Delete files/folders | ✅ Done | Long-press → Delete → confirmation dialog |
| 8 | Open files in respective apps | ✅ Done | Tap file → system chooser via FileProvider + MIME type |
| 9 | Storage permission handling | ✅ Done | MANAGE_EXTERNAL_STORAGE for Android 11+; legacy for older |
| 10 | File metadata display (size, date) | ✅ Done | Size for files, item count for folders, last modified date |
| 21 | Pull to refresh in each folder | ✅ Done | PullToRefreshBox wraps file list; refresh() reloads current directory |
| 11 | Create new folder | ✅ Done | "+" menu in top bar → New Folder → name dialog → creates folder |
| 12 | Create new file | ✅ Done | "+" menu in top bar → New File → name dialog → creates empty file |
| 22a | Home screen with category cards | ✅ Done | Grid layout with File Explorer, Recent Files, Photos, Music, Videos, Documents, Apps |
| 22b | Recent Files screen | ✅ Done | Recursive scan of storage; sorted by date descending; shows name, path, size, date/time |
| 21 | Preview thumbnails for files | ✅ Done | Coil-based: image thumbnails, video frame thumbnails, typed icons for PDF/audio/docs/APK |

## Pending / Future Features

| #  | Feature                                                                                                                | Status | Notes |
|----|------------------------------------------------------------------------------------------------------------------------|--------|-------|
| 13 | Search files/folders                                                                                                   | 🔲 Pending | |
| 14 | Sort options (name, size, date, type)                                                                                  | 🔲 Pending | |
| 15 | Multi-select files for batch operations                                                                                | 🔲 Pending | |
| 16 | Zip/Unzip support                                                                                                      | 🔲 Pending | |
| 17 | Bookmarks / Favorites                                                                                                  | 🔲 Pending | |
| 18 | Storage usage overview                                                                                                 | 🔲 Pending | |
| 19 | Dark/Light theme toggle                                                                                                | 🔲 Pending | Currently follows system theme |
| 20 | Settings screen                                                                                                        | 🔲 Pending | |
| 22c | Photos screen                                                                                                          | 🔲 Pending | |
| 22d | Music screen                                                                                                           | 🔲 Pending | |
| 22e | Videos screen                                                                                                          | 🔲 Pending | |
| 22f | Documents screen                                                                                                       | 🔲 Pending | |
| 22g | Applications screen                                                                                                    | 🔲 Pending | |
| 23 | Open app with in screen keyboard for numbers only to allow entering 6 digit number password to go to files list screen | 🔲 Pending | |

---

*Add new feature requests below the Pending table. Update status when implemented.*

