# Update Video Seekbar to Circular Knob (VLC Style)

Change the seekbar thumb from a cursor-like line to a circular knob and adjust track thickness to match VLC's style.

## User Review Required

> [!NOTE]
> I will be using a custom `thumb` for the `Slider` to ensure it appears as a solid circular knob. I will also slightly increase the track thickness to better match the VLC screenshot.

## Proposed Changes

### [MediaViewerScreen.kt](file:///C:/Users/Admin/AndroidStudioProjects/Files/app/src/main/java/com/gmail/omkarjoshi1989/ui/screens/MediaViewerScreen.kt)

#### [MODIFY] [VideoControls](file:///C:/Users/Admin/AndroidStudioProjects/Files/app/src/main/java/com/gmail/omkarjoshi1989/ui/screens/MediaViewerScreen.kt)

1.  **Add `DpSize` Import**: Add `import androidx.compose.ui.unit.DpSize` to the imports.
2.  **Initialize `interactionSource`**: Add `val sliderInteractionSource = remember { MutableInteractionSource() }` within `VideoControls`.
3.  **Customize `Slider`**:
    *   Add `interactionSource = sliderInteractionSource`.
    *   Provide a custom `thumb` using `SliderDefaults.Thumb` with `thumbSize = DpSize(12.dp, 12.dp)`.
    *   Provide a custom `track` using `SliderDefaults.Track` (if available) or a simplified version to ensure no gaps and appropriate thickness.
    *   Maintain the `#FF8800` (VLC orange) color scheme.

## Verification Plan

### Automated Tests
*   Run `:app:compileDebugKotlin` to verify the build.

### Manual Verification
*   Open a video.
*   Verify the seekbar thumb is a circular orange knob.
*   Verify the track thickness matches the VLC style more closely.
