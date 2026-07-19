# Fix Video Brightness and Volume Gesture Stability

Improve the smoothness and reliability of the vertical drag gestures for brightness and volume in the video player.

## User Review Required

> [!IMPORTANT]
> This change fixes the "jumping" behavior where brightness would reset to an old value at the start of a new drag. It also makes the adjustment more gradual by using an accumulated delta approach.

## Proposed Changes

### [MediaViewerScreen.kt](file:///C:/Users/Admin/AndroidStudioProjects/Files/app/src/main/java/com/gmail/omkarjoshi1989/ui/screens/MediaViewerScreen.kt)

#### [MODIFY] [VideoPage](file:///C:/Users/Admin/AndroidStudioProjects/Files/app/src/main/java/com/gmail/omkarjoshi1989/ui/screens/MediaViewerScreen.kt)

1.  **Fix Stale Captures**:
    *   Wrap `brightness` and `onBrightnessChange` in `rememberUpdatedState` to allow `pointerInput` lambdas to access the current values without needing to restart the gesture detection.
2.  **Refactor Drag Logic**:
    *   Change the logic to capture `initialValue` at `onDragStart`.
    *   In `onVerticalDrag`, calculate the `newVal` as `initialValue + (totalDelta / height)`.
    *   This prevents the "irregular" jumps and ensures the brightness always starts from the *actual* current level.
3.  **Apply to Volume**:
    *   Apply the same stable logic to volume adjustment for consistency.

## Verification Plan

### Automated Tests
*   Run `:app:compileDebugKotlin` to verify no regressions.

### Manual Verification
*   Open a video.
*   Adjust brightness, release, and start adjusting again: verify it continues from the new level without jumping back.
*   Verify that dragging up and then down within the same gesture behaves smoothly and predictably.
