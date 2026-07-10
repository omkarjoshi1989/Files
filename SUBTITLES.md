# Internal Subtitle Selection Support

This document outlines the plan to add support for selecting internal subtitle tracks from video files in the Media Viewer.

## Current State
- The app currently supports external subtitle files (e.g., `.srt`) if they are in the same folder as the video file.
- It does not provide a UI for selecting between multiple internal subtitle tracks (e.g., in MKV or MP4 files).

## Proposed Changes

### 1. Track Selection State
Add state variables to `VideoPage` in `MediaViewerScreen.kt` to track available and selected subtitle tracks.

```kotlin
var availableSubtitleTracks by remember { mutableStateOf<List<Triple<Int, Int, String>>>(emptyList()) }
var selectedSubtitleGroupIdx by remember { mutableIntStateOf(-1) }
var selectedSubtitleTrackIdx by remember { mutableIntStateOf(-1) }
```

### 2. Scanning for Subtitle Tracks
Update the `onTracksChanged` listener in `VideoPage` to identify subtitle (text) tracks.

```kotlin
// Inside onTracksChanged
for (group in tracks.groups) {
    if (group.type == C.TRACK_TYPE_TEXT) {
        for (trackIdx in 0 until group.length) {
            val format = group.getTrackFormat(trackIdx)
            val displayName = format.label ?: format.language ?: "Subtitle ${newSubTracks.size + 1}"
            newSubTracks.add(Triple(groupIdx, trackIdx, displayName))
            if (group.isTrackSelected(trackIdx)) {
                selSubGroup = groupIdx
                selSubTrack = trackIdx
            }
        }
    }
    groupIdx++
}
```

### 3. Settings Menu UI
Extend the existing `PopupMenu` in the video controller to include a "Subtitles" section.

- Show a list of available internal subtitle tracks.
- Add an "Off" option to disable subtitles.
- Mark the currently selected track.

### 4. Implementation Details
- Use `exoPlayer.trackSelectionParameters` to apply the selected track.
- To disable subtitles, set the text track type as disabled or provide an override that selects no tracks.

## Verification
- Test with MKV files containing multiple subtitle tracks.
- Test with MP4 files containing internal subtitles.
- Verify that external subtitles still work as a default option.
