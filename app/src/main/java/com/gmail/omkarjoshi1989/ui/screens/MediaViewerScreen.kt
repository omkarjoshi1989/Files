package com.gmail.omkarjoshi1989.ui.screens

import android.app.Activity
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.foundation.shape.CircleShape
// Shuffle removed: no import
import androidx.compose.material3.Slider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntSize
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.size.Precision
import coil.size.Size as CoilSize
import coil.request.ImageRequest
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import com.gmail.omkarjoshi1989.MediaViewerActivity
import com.gmail.omkarjoshi1989.service.MusicPlaybackService
import com.gmail.omkarjoshi1989.util.FileUtils
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.CircularProgressIndicator
import coil.imageLoader
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.width
import androidx.core.content.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import com.gmail.omkarjoshi1989.util.FavoritesManager
import com.gmail.omkarjoshi1989.util.RecycleBinManager
import com.gmail.omkarjoshi1989.util.SmbSeekableDataSourceFactory
import com.gmail.omkarjoshi1989.util.SubtitleSidecarResolver

private enum class ImageFitMode(val label: String, val contentScale: ContentScale) {
    Fit("Fit", ContentScale.Fit),
    Fill("Fill", ContentScale.Crop),
    Original("Original", ContentScale.None)
}

private enum class ImageCropPreset(val label: String, val ratio: Float?) {
    Free("Free", null),
    Square("1:1", 1f),
    Portrait("4:5", 4f / 5f),
    Wide("16:9", 16f / 9f)
}

private enum class ImageAdjustmentPreset(val label: String) {
    Auto("Auto"),
    BlackWhite("B&W"),
    Vivid("Vivid"),
    Soft("Soft"),
    Custom("Custom")
}

private enum class ImageEditorMode {
    Full,
    CropOnly
}

private data class ImageEditRecipe(
    val rotationQuarterTurns: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val cropPreset: ImageCropPreset = ImageCropPreset.Free,
    val brightnessAdj: Float = 0f,
    val contrastAdj: Float = 1f,
    val saturationAdj: Float = 1f,
    val warmthAdj: Float = 0f,
    val highlightsAdj: Float = 0f,
    val shadowsAdj: Float = 0f,
    val adjustmentPreset: ImageAdjustmentPreset = ImageAdjustmentPreset.Custom
) {
    val normalizedQuarterTurns: Int
        get() = ((rotationQuarterTurns % 4) + 4) % 4
    val isIdentity: Boolean
        get() = normalizedQuarterTurns == 0 &&
            !flipHorizontal &&
            !flipVertical &&
            cropPreset == ImageCropPreset.Free &&
            brightnessAdj == 0f &&
            contrastAdj == 1f &&
            saturationAdj == 1f &&
            warmthAdj == 0f &&
            highlightsAdj == 0f &&
            shadowsAdj == 0f
}

private fun ImageEditRecipe.withAdjustmentPreset(preset: ImageAdjustmentPreset): ImageEditRecipe {
    return when (preset) {
        ImageAdjustmentPreset.Auto -> copy(
            brightnessAdj = 0f,
            contrastAdj = 1.05f,
            saturationAdj = 1.08f,
            warmthAdj = 0.05f,
            highlightsAdj = -0.05f,
            shadowsAdj = 0.10f,
            adjustmentPreset = preset
        )
        ImageAdjustmentPreset.BlackWhite -> copy(
            brightnessAdj = 0f,
            contrastAdj = 1.08f,
            saturationAdj = 0f,
            warmthAdj = 0f,
            highlightsAdj = 0f,
            shadowsAdj = 0.05f,
            adjustmentPreset = preset
        )
        ImageAdjustmentPreset.Vivid -> copy(
            brightnessAdj = 0f,
            contrastAdj = 1.15f,
            saturationAdj = 1.25f,
            warmthAdj = 0.08f,
            highlightsAdj = 0.05f,
            shadowsAdj = -0.05f,
            adjustmentPreset = preset
        )
        ImageAdjustmentPreset.Soft -> copy(
            brightnessAdj = 0.03f,
            contrastAdj = 0.92f,
            saturationAdj = 0.88f,
            warmthAdj = 0.02f,
            highlightsAdj = -0.10f,
            shadowsAdj = 0.15f,
            adjustmentPreset = preset
        )
        ImageAdjustmentPreset.Custom -> copy(adjustmentPreset = preset)
    }
}

private fun identityColorMatrixValues(): FloatArray = floatArrayOf(
    1f, 0f, 0f, 0f, 0f,
    0f, 1f, 0f, 0f, 0f,
    0f, 0f, 1f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f
)

private fun multiplyColorMatrices(left: FloatArray, right: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (row in 0..3) {
        for (col in 0..4) {
            val idx = row * 5 + col
            out[idx] = if (col == 4) {
                left[row * 5 + 4] +
                    left[row * 5] * right[4] +
                    left[row * 5 + 1] * right[9] +
                    left[row * 5 + 2] * right[14] +
                    left[row * 5 + 3] * right[19]
            } else {
                left[row * 5] * right[col] +
                    left[row * 5 + 1] * right[5 + col] +
                    left[row * 5 + 2] * right[10 + col] +
                    left[row * 5 + 3] * right[15 + col]
            }
        }
    }
    return out
}

private fun ImageEditRecipe.buildAdjustmentMatrixValues(): FloatArray? {
    val hasAdjustments =
        brightnessAdj != 0f || contrastAdj != 1f || saturationAdj != 1f ||
            warmthAdj != 0f || highlightsAdj != 0f || shadowsAdj != 0f
    if (!hasAdjustments) return null

    var matrix = identityColorMatrixValues()

    val contrast = contrastAdj.coerceIn(0.5f, 1.6f)
    val contrastTranslate = (1f - contrast) * 128f
    matrix = multiplyColorMatrices(floatArrayOf(
        contrast, 0f, 0f, 0f, contrastTranslate,
        0f, contrast, 0f, 0f, contrastTranslate,
        0f, 0f, contrast, 0f, contrastTranslate,
        0f, 0f, 0f, 1f, 0f
    ), matrix)

    val saturation = saturationAdj.coerceIn(0f, 2f)
    val invSat = 1f - saturation
    val r = 0.213f * invSat; val g = 0.715f * invSat; val b = 0.072f * invSat
    matrix = multiplyColorMatrices(floatArrayOf(
        r + saturation, g, b, 0f, 0f,
        r, g + saturation, b, 0f, 0f,
        r, g, b + saturation, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ), matrix)

    val warmth = warmthAdj.coerceIn(-1f, 1f)
    val warmthRed = (1f + warmth * 0.18f).coerceIn(0.75f, 1.25f)
    val warmthBlue = (1f - warmth * 0.18f).coerceIn(0.75f, 1.25f)
    matrix = multiplyColorMatrices(floatArrayOf(
        warmthRed, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, warmthBlue, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ), matrix)

    val highlights = highlightsAdj.coerceIn(-1f, 1f)
    val shadows = shadowsAdj.coerceIn(-1f, 1f)
    val tonalScale = (1f - highlights * 0.12f + shadows * 0.04f).coerceIn(0.75f, 1.25f)
    val tonalOffset = shadows * 28f - highlights * 20f
    matrix = multiplyColorMatrices(floatArrayOf(
        tonalScale, 0f, 0f, 0f, tonalOffset,
        0f, tonalScale, 0f, 0f, tonalOffset,
        0f, 0f, tonalScale, 0f, tonalOffset,
        0f, 0f, 0f, 1f, 0f
    ), matrix)

    val brightness = brightnessAdj.coerceIn(-0.5f, 0.5f) * 255f
    matrix = multiplyColorMatrices(floatArrayOf(
        1f, 0f, 0f, 0f, brightness,
        0f, 1f, 0f, 0f, brightness,
        0f, 0f, 1f, 0f, brightness,
        0f, 0f, 0f, 1f, 0f
    ), matrix)

    return matrix
}

private fun ImageEditRecipe.toColorFilterOrNull(): ColorFilter? {
    val values = buildAdjustmentMatrixValues() ?: return null
    return ColorFilter.colorMatrix(ColorMatrix(values))
}

private suspend fun applyEditRecipeAndSave(
    context: Context,
    sourceFile: File,
    recipe: ImageEditRecipe,
    saveCopy: Boolean
): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        val sourceBitmap = try {
            BitmapFactory.decodeFile(sourceFile.absolutePath)
                ?: error("Failed to decode image: ${sourceFile.name}")
        } catch (e: OutOfMemoryError) {
            throw java.io.IOException("Image is too large to process — close other apps and try again")
        }

        // Build rotation + flip matrix
        val m = android.graphics.Matrix()
        val degrees = recipe.normalizedQuarterTurns * 90f
        if (degrees != 0f) m.postRotate(degrees)
        val cx = sourceBitmap.width / 2f; val cy = sourceBitmap.height / 2f
        if (recipe.flipHorizontal) m.postScale(-1f, 1f, cx, cy)
        if (recipe.flipVertical) m.postScale(1f, -1f, cx, cy)

        var bitmap: Bitmap = if (!m.isIdentity) {
            Bitmap.createBitmap(sourceBitmap, 0, 0, sourceBitmap.width, sourceBitmap.height, m, true)
                .also { if (it !== sourceBitmap) sourceBitmap.recycle() }
        } else sourceBitmap

        // Center-crop to preset ratio
        val cropRatio = recipe.cropPreset.ratio
        if (cropRatio != null) {
            val w = bitmap.width; val h = bitmap.height
            val currentRatio = w.toFloat() / h
            val (newW, newH) = if (currentRatio > cropRatio) {
                ((h * cropRatio).toInt()) to h
            } else {
                w to ((w / cropRatio).toInt())
            }
            val cx2 = (w - newW) / 2; val cy2 = (h - newH) / 2
            val cropped = Bitmap.createBitmap(bitmap, cx2, cy2, newW.coerceAtLeast(1), newH.coerceAtLeast(1))
            if (cropped !== bitmap) bitmap.recycle()
            bitmap = cropped
        }

        // Apply color adjustments
        val matrixValues = recipe.buildAdjustmentMatrixValues()
        if (matrixValues != null) {
            val androidCm = android.graphics.ColorMatrix(matrixValues)
            val paint = android.graphics.Paint().apply {
                colorFilter = android.graphics.ColorMatrixColorFilter(androidCm)
            }
            val adjusted = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(adjusted).drawBitmap(bitmap, 0f, 0f, paint)
            bitmap.recycle()
            bitmap = adjusted
        }

        // Write to temp file
        val ext = sourceFile.extension.lowercase()
        val isPng = ext == "png"
        val parentDir = sourceFile.parentFile
            ?: error("Source file has no parent directory")
        if (!parentDir.canWrite()) error("Destination folder is read-only")
        val tempFile = java.io.File.createTempFile("img_edit_", ".$ext", parentDir)
        try {
            FileOutputStream(tempFile).use { fos ->
                val ok = if (isPng) bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
                         else bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                if (!ok) error("Bitmap compression failed")
            }
        } catch (e: java.io.IOException) {
            tempFile.delete()
            if (e.message?.contains("space", ignoreCase = true) == true ||
                e.message?.contains("ENOSPC", ignoreCase = true) == true
            ) {
                throw java.io.IOException("Not enough storage space to save the image")
            }
            throw e
        }
        bitmap.recycle()

        // Copy EXIF metadata (JPEG only, best-effort)
        if (!isPng) {
            try {
                val srcExif = android.media.ExifInterface(sourceFile.absolutePath)
                val dstExif = android.media.ExifInterface(tempFile.absolutePath)
                listOf(
                    android.media.ExifInterface.TAG_DATETIME,
                    android.media.ExifInterface.TAG_DATETIME_ORIGINAL,
                    android.media.ExifInterface.TAG_GPS_LATITUDE,
                    android.media.ExifInterface.TAG_GPS_LATITUDE_REF,
                    android.media.ExifInterface.TAG_GPS_LONGITUDE,
                    android.media.ExifInterface.TAG_GPS_LONGITUDE_REF,
                    android.media.ExifInterface.TAG_GPS_ALTITUDE,
                    android.media.ExifInterface.TAG_GPS_ALTITUDE_REF,
                    android.media.ExifInterface.TAG_MAKE,
                    android.media.ExifInterface.TAG_MODEL
                ).forEach { tag ->
                    srcExif.getAttribute(tag)?.let { dstExif.setAttribute(tag, it) }
                }
                dstExif.setAttribute(
                    android.media.ExifInterface.TAG_ORIENTATION,
                    android.media.ExifInterface.ORIENTATION_NORMAL.toString()
                )
                dstExif.saveAttributes()
            } catch (_: Exception) { /* best-effort */ }
        }

        // Move to final location
        val finalFile = if (saveCopy) {
            val base = sourceFile.nameWithoutExtension
            val parent = sourceFile.parentFile ?: error("No parent directory")
            var candidate = java.io.File(parent, "${base}_edited.$ext")
            var n = 1
            while (candidate.exists()) { candidate = java.io.File(parent, "${base}_edited_$n.$ext"); n++ }
            tempFile.renameTo(candidate)
            candidate
        } else {
            sourceFile.delete()
            if (!tempFile.renameTo(sourceFile)) {
                // Fallback copy if rename fails (cross-device)
                tempFile.inputStream().use { inp -> sourceFile.outputStream().use { out -> inp.copyTo(out) } }
                tempFile.delete()
            }
            sourceFile
        }

        // Notify MediaStore
        android.media.MediaScannerConnection.scanFile(context, arrayOf(finalFile.absolutePath), null, null)
        finalFile
    }
}

private fun classifySaveError(err: Throwable): String = when {
    err is OutOfMemoryError ->
        "Image is too large to process — close other apps and try again"
    err.message?.contains("space", ignoreCase = true) == true ||
        err.message?.contains("ENOSPC", ignoreCase = true) == true ->
        "Not enough storage space to save the image"
    err.message?.contains("read-only", ignoreCase = true) == true ||
        err.message?.contains("Permission denied", ignoreCase = true) == true ->
        "Cannot write to this location — file may be read-only"
    else -> err.message ?: "Save failed"
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaViewerScreen(
    mediaFiles: List<File>,
    initialIndex: Int,
    loopEnabled: Boolean = false,
    autoPlay: Boolean = true,
    isInPipMode: Boolean = false,
    onEnterPip: () -> Unit = {},
    onVideoPageChanged: (Boolean) -> Unit = {},
    onVideoPlayingChanged: (Boolean) -> Unit = {},
    onFileDeleted: (File) -> Unit = {},
    onFileAdded: (File) -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current

    // ── Loop swiping: use a large virtual page count so user can swipe endlessly ──
    val actualCount = mediaFiles.size
    val virtualPageCount = if (loopEnabled && actualCount > 1) actualCount * 10_000 else actualCount
    val virtualInitialPage = if (loopEnabled && actualCount > 1) {
        // Start in the middle of the virtual range at the correct offset
        (5_000 * actualCount) + initialIndex
    } else {
        initialIndex
    }

    val pagerState = rememberPagerState(
        initialPage = virtualInitialPage,
        pageCount = { virtualPageCount }
    )

    // Map virtual page to actual index
    fun virtualToActual(virtualPage: Int): Int {
        return if (loopEnabled && actualCount > 1) virtualPage.mod(actualCount) else virtualPage
    }

    val currentFile = mediaFiles.getOrNull(virtualToActual(pagerState.settledPage))
    val isCurrentFileAudio = currentFile != null && FileUtils.isAudioFile(currentFile)
    var isImmersive by remember { mutableStateOf(false) }

    // ── Image viewer menu state ─────────────────────────────────────────
    val isCurrentFileImage = currentFile != null && FileUtils.isImageFile(currentFile)
    var showImageMenu by remember { mutableStateOf(false) }
    var isFavorite by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showBrightnessSheet by remember { mutableStateOf(false) }
    var slideshowActive by remember { mutableStateOf(false) }
    var isCurrentImageZoomed by remember { mutableStateOf(false) }
    var imageResetRequest by remember { mutableIntStateOf(0) }
    var imageFitMode by remember { mutableStateOf(ImageFitMode.Fit) }
    var showImageHud by remember { mutableStateOf(true) }
    var showImageEditor by remember { mutableStateOf(false) }
    var imageEditorMode by remember { mutableStateOf(ImageEditorMode.Full) }
    var appliedImageEdits by remember { mutableStateOf<Map<String, ImageEditRecipe>>(emptyMap()) }
    var editHistory by remember { mutableStateOf(listOf(ImageEditRecipe())) }
    var editHistoryIndex by remember { mutableIntStateOf(0) }
    val brightnessSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val currentEditRecipe = editHistory.getOrElse(editHistoryIndex) { ImageEditRecipe() }
    val coroutineScope = rememberCoroutineScope()
    var activeSaveJob by remember { mutableStateOf<Job?>(null) }
    var showSaveProgress by remember { mutableStateOf(false) }
    var showReplaceConfirmDialog by remember { mutableStateOf(false) }
    var saveResultMessage by remember { mutableStateOf<String?>(null) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { activeSaveJob?.cancel() }
    }

    // ── Swipe lock: disable left/right swipe when in landscape + video page ──
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val isCurrentFileVideo = currentFile != null && FileUtils.isVideoFile(currentFile)
    val pagerScrollEnabled = !(isLandscape && isCurrentFileVideo) &&
        !(isCurrentFileImage && isCurrentImageZoomed) &&
        !showImageEditor

    // Notify the host activity when the current page type changes (for auto-PiP)
    LaunchedEffect(isCurrentFileVideo) {
        onVideoPageChanged(isCurrentFileVideo)
    }

    // ── Screen-level brightness override (shared across video pages) ────
    val screenActivity = remember(context) {
        var c: Context? = context
        while (c is android.content.ContextWrapper && c !is Activity) c = c.baseContext
        c as? Activity
    }
    var screenBrightness by remember(screenActivity) {
        val seeded = if (screenActivity == null) {
            0.5f
        } else {
            val current = screenActivity.window.attributes.screenBrightness
            if (current >= 0f) {
                current
            } else {
                try {
                    Settings.System.getInt(
                        screenActivity.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS
                    ) / 255f
                } catch (_: Exception) {
                    0.5f
                }
            }
        }
        mutableStateOf(seeded.coerceIn(0f, 1f))
    }
    LaunchedEffect(screenActivity, screenBrightness) {
        screenActivity?.let {
            val lp = it.window.attributes
            lp.screenBrightness = screenBrightness.coerceIn(0.01f, 1f)
            it.window.attributes = lp
        }
    }
    DisposableEffect(screenActivity) {
        onDispose {
            screenActivity?.let {
                val lp = it.window.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.window.attributes = lp
            }
        }
    }

    // ── Audio playlist mapping ──────────────────────────────────────────
    // audioPageIndices: list of pager indices that are audio files (in pager order)
    // pageToPlaylistIndex: pager page index → playlist position
    // playlistToPageIndex: playlist position → pager page index
    val audioPageIndices = remember(mediaFiles) {
        mediaFiles.mapIndexedNotNull { index, file ->
            if (FileUtils.isAudioFile(file)) index else null
        }
    }
    val pageToPlaylistIndex = remember(audioPageIndices) {
        audioPageIndices.withIndex().associate { (playlistIdx, pageIdx) -> pageIdx to playlistIdx }
    }
    val playlistToPageIndex = remember(audioPageIndices) {
        audioPageIndices.withIndex().associate { (playlistIdx, pageIdx) -> playlistIdx to pageIdx }
    }
    val hasAudioFiles = audioPageIndices.isNotEmpty()

    // ── Shared music controller state (hoisted) ─────────────────────────
    var musicController by remember { mutableStateOf<MediaController?>(null) }
    var isAudioPlaying by remember { mutableStateOf(false) }
    var audioPosition by remember { mutableLongStateOf(0L) }
    var audioDuration by remember { mutableLongStateOf(0L) }
    // repeat/shuffle state are maintained per-audio page below
    // Hoist repeat mode so the UI can reflect the player's repeat state across pages.
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_ALL) }
    // Tracks the player's current media-item index inside the playlist.
    // -1 means "no audio track active yet".
    var playerTrackIndex by remember { mutableIntStateOf(-1) }
    // Whether we have already loaded our playlist into the service player.
    var playlistLoaded by remember { mutableStateOf(false) }
    // True while the pager is being scrolled programmatically (due to a player
    // track auto-advance).  Prevents snapshotFlow from calling syncAudioToPage
    // and seeking the player backward to an intermediate/stale page.
    var isProgrammaticPagerScroll by remember { mutableStateOf(false) }
    // Incremented on ON_RESUME to trigger an instant pager snap when the pager
    // is stale (animations couldn't run while the screen was off).
    var resumeSyncNeeded by remember { mutableStateOf(false) }
    // When autoPlay=false (launched from toolbar "resume" button), suppress the
    // very first mc.play() so the player loads the track but stays paused until
    // the user taps the play button themselves.
    var initialLoadDone by remember { mutableStateOf(autoPlay) }

    // Build MediaItem list once (stable across recompositions)
    val audioMediaItems = remember(mediaFiles, audioPageIndices) {
        audioPageIndices.map { pageIdx ->
            val file = mediaFiles[pageIdx]
            MediaItem.Builder()
                .setUri(android.net.Uri.fromFile(file))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                                .setTitle(stripNumericPrefix(file.nameWithoutExtension))
                        .setArtist("Files App")
                        .build()
                )
                .build()
        }
    }

    // ── Connect to MusicPlaybackService once at screen level ────────────
    if (hasAudioFiles) {
        DisposableEffect(Unit) {
            val sessionToken = SessionToken(
                context,
                android.content.ComponentName(context, MusicPlaybackService::class.java)
            )
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()

            controllerFuture.addListener(
                {
                    val mc = controllerFuture.get()
                    musicController = mc

                    // Check if the service already has the exact same playlist loaded
                    val samePlaylist = mc.mediaItemCount == audioMediaItems.size &&
                            audioMediaItems.indices.all { i ->
                                mc.getMediaItemAt(i).localConfiguration?.uri ==
                                        audioMediaItems[i].localConfiguration?.uri
                            }

                    if (samePlaylist) {
                        // Playlist already loaded (e.g. returning from notification) — sync UI
                        playlistLoaded = true
                        isAudioPlaying = mc.isPlaying
                        if (mc.duration > 0) audioDuration = mc.duration
                        audioPosition = mc.currentPosition
                        playerTrackIndex = mc.currentMediaItemIndex
                        // Sync the UI repeat mode from the player
                        try { repeatMode = mc.repeatMode } catch (_: Exception) { }
                    } else {
                        // If we are about to load a new playlist later, prefer the UI's repeatMode
                        try { mc.repeatMode = repeatMode } catch (_: Exception) { }
                    }

                    // Listen for state changes from the player / notification controls
                    mc.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(playing: Boolean) {
                            isAudioPlaying = playing
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                audioDuration = mc.duration
                            }
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            playerTrackIndex = mc.currentMediaItemIndex
                            audioDuration = if (mc.duration > 0) mc.duration else 0L
                            audioPosition = mc.currentPosition
                        }

                        override fun onRepeatModeChanged(repeatModeFromPlayer: Int) {
                            repeatMode = repeatModeFromPlayer
                        }
                    })
                 },
                 MoreExecutors.directExecutor()
             )

            onDispose {
                MediaController.releaseFuture(controllerFuture)
                musicController = null
            }
        }
    }

    // ── Helper: load/seek the audio playlist and auto-play ─────────────
    // Extracted so both the snapshotFlow and the controller-ready effect
    // can call the same logic without duplication.
    fun syncAudioToPage(mc: MediaController, page: Int) {
        val playlistIdx = pageToPlaylistIndex[page] ?: return
        // Treat the playlist as unloaded if either we haven't loaded it yet, OR
        // the service was killed while the screen was off/idle (mediaItemCount == 0).
        val needsReload = !playlistLoaded || mc.mediaItemCount == 0
        if (needsReload) {
            // First time landing on audio, or service was restarted — reload playlist
            mc.setMediaItems(audioMediaItems, playlistIdx, 0L)
            mc.prepare()
            // Do not override existing repeatMode here; the UI's hoisted `repeatMode`
            // was already applied to the controller when it connected. This preserves
            // the user's repeat choice across swipes.
            if (initialLoadDone) {
                mc.play()                  // ← auto-play
            } else {
                // First open from toolbar — prepare the track but wait for user to tap play
                initialLoadDone = true
            }
            playlistLoaded = true
            playerTrackIndex = playlistIdx
        } else if (mc.currentMediaItemIndex != playlistIdx) {
            mc.seekToDefaultPosition(playlistIdx)
            mc.play()                      // ← auto-play on swipe
            playerTrackIndex = playlistIdx
        } else if (!mc.isPlaying) {
            // Same track but paused (e.g. swiped away to image then back,
            // or player reached STATE_IDLE/STATE_ENDED after long idle).
            when (mc.playbackState) {
                Player.STATE_IDLE -> { mc.prepare(); mc.play() }
                Player.STATE_ENDED -> { mc.seekToDefaultPosition(playlistIdx); mc.play() }
                else -> mc.play()
            }
        }
        // Sync UI immediately
        isAudioPlaying = mc.isPlaying
        audioDuration = if (mc.duration > 0) mc.duration else 0L
        audioPosition = mc.currentPosition
    }

    // ── Helper: resume/play current audio, recovering from service kill ─────
    fun playCurrentAudio() {
        val mc = musicController ?: return
        val actualPage = virtualToActual(pagerState.settledPage)
        val playlistIdx = pageToPlaylistIndex[actualPage] ?: return
        when {
            mc.mediaItemCount == 0 || !playlistLoaded -> {
                // Service was killed (no media items) or never loaded — reload playlist,
                // resuming from the last known position so the user doesn't lose progress.
                mc.setMediaItems(audioMediaItems, playlistIdx, audioPosition.coerceAtLeast(0L))
                mc.prepare()
                mc.play()
                playlistLoaded = true
                playerTrackIndex = playlistIdx
            }
            mc.playbackState == Player.STATE_ENDED -> {
                mc.seekToDefaultPosition(playlistIdx)
                mc.play()
            }
            mc.playbackState == Player.STATE_IDLE -> {
                mc.prepare()
                mc.play()
            }
            else -> mc.play()
        }
    }

    // ── Pager page settled → seek player or pause ────────────────────────
    // Uses settledPage so playback only starts after the swipe animation
    // finishes, preventing audio blips on intermediate pages.
    LaunchedEffect(Unit) {
        snapshotFlow { pagerState.settledPage }.collect { page ->

            val actualPage = virtualToActual(page)

            val playlistIdx = pageToPlaylistIndex[actualPage]
            if (playlistIdx != null) {
                if (!isProgrammaticPagerScroll) {
                    // User-initiated swipe: seek the player to the new page's track.
                    musicController?.let { mc -> syncAudioToPage(mc, actualPage) }
                } else {
                    // Programmatic scroll (player auto-advanced to next track):
                    // Do NOT seek the player — it is already on the correct track.
                    // Only refresh the UI position/duration state.
                    musicController?.let { mc ->
                        isAudioPlaying = mc.isPlaying
                        if (mc.duration > 0) audioDuration = mc.duration
                        audioPosition = mc.currentPosition
                    }
                }
            } else {
                // Non-audio page — pause audio if playing
                musicController?.let { mc ->
                    if (mc.isPlaying) mc.pause()
                }
            }
        }
    }

    // ── Controller connected → catch up with current page ────────────────
    // Fixes the race where the snapshotFlow already fired before the
    // MediaController was ready (musicController was null at that point).
    LaunchedEffect(musicController) {
        val mc = musicController ?: return@LaunchedEffect
        val actualPage = virtualToActual(pagerState.settledPage)
        val playlistIdx = pageToPlaylistIndex[actualPage]
        if (playlistIdx != null) {
            syncAudioToPage(mc, actualPage)
        }
    }

    // ── Player track changed externally (notification skip) → scroll pager ──
    LaunchedEffect(playerTrackIndex) {
        if (playerTrackIndex >= 0) {
            val targetActualPage = playlistToPageIndex[playerTrackIndex]
            if (targetActualPage != null) {
                val currentActualPage = virtualToActual(pagerState.settledPage)
                if (currentActualPage != targetActualPage) {
                    // Mark as programmatic so snapshotFlow won't re-seek the player
                    // backward when the pager settles (including on a cancelled animation).
                    isProgrammaticPagerScroll = true
                    try {
                        if (loopEnabled && actualCount > 1) {
                            // Calculate the nearest virtual page for the target actual page
                            val currentVirtual = pagerState.settledPage
                            val currentOffset = currentVirtual - currentActualPage
                            val targetVirtual = currentOffset + targetActualPage
                            pagerState.animateScrollToPage(targetVirtual)
                        } else {
                            pagerState.animateScrollToPage(targetActualPage)
                        }
                    } finally {
                        // Always clear the flag — both on successful completion and
                        // on cancellation (when the next track fires before this
                        // animation finishes).  The next LaunchedEffect will re-set it.
                        isProgrammaticPagerScroll = false
                    }
                }
            }
        }
    }

    // ── Periodic position update while audio is playing ─────────────────
    // Keep position updated regardless of play state so the seekbar stays in sync.
    LaunchedEffect(musicController) {
        while (musicController != null) {
            musicController?.let { audioPosition = it.currentPosition; audioDuration = if (it.duration > 0) it.duration else audioDuration }
            delay(500)
        }
    }

    // ── Re-sync state after screen unlock / returning from background ─────
    // When the screen turns off and back on (or the app is resumed from background),
    // the player state may have drifted (e.g. service killed, audio focus lost).
    // This observer catches ON_RESUME to refresh all UI state and mark the playlist
    // as needing a reload if the service was killed (mediaItemCount == 0).
    if (hasAudioFiles) {
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    val mc = musicController ?: return@LifecycleEventObserver
                    // Refresh UI state from the actual player
                    isAudioPlaying = mc.isPlaying
                    if (mc.duration > 0) audioDuration = mc.duration
                    audioPosition = mc.currentPosition
                    // If service was killed (player has no media items), mark playlist as
                    // unloaded so the next play press (or syncAudioToPage call) reloads it.
                    if (mc.mediaItemCount == 0) {
                        playlistLoaded = false
                    } else {
                        // Pager may be stale: animations don't run with screen off, so
                        // settledPage might still point at the track that was playing when
                        // the screen turned off.  Request an instant snap to the correct page.
                        val currentActualPage = virtualToActual(pagerState.settledPage)
                        val expectedPage = playlistToPageIndex[mc.currentMediaItemIndex]
                        if (expectedPage != null && currentActualPage != expectedPage) {
                            resumeSyncNeeded = !resumeSyncNeeded  // toggle to trigger LaunchedEffect
                        }
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    // ── Instantly snap the pager to the player's current track on resume ──
    // Runs whenever resumeSyncNeeded flips, which means the screen just came
    // back on and the pager is pointing at a stale track.
    if (hasAudioFiles) {
        LaunchedEffect(resumeSyncNeeded) {
            val mc = musicController ?: return@LaunchedEffect
            val targetActualPage = playlistToPageIndex[mc.currentMediaItemIndex]
                ?: return@LaunchedEffect
            val currentActualPage = virtualToActual(pagerState.settledPage)
            if (currentActualPage != targetActualPage) {
                val targetVirtual = if (loopEnabled && actualCount > 1) {
                    pagerState.settledPage - currentActualPage + targetActualPage
                } else targetActualPage
                isProgrammaticPagerScroll = true
                try {
                    // scrollToPage = instant snap (no animation); avoids the
                    // jerky "catch-up" animation that would otherwise play.
                    pagerState.scrollToPage(targetVirtual)
                } finally {
                    isProgrammaticPagerScroll = false
                }
            }
        }
    }

    // ── Sync isFavorite when the current image page changes ────────────
    LaunchedEffect(currentFile) {
        isFavorite = currentFile
            ?.takeIf { FileUtils.isImageFile(it) }
            ?.let { FavoritesManager.isFavorite(context, it.absolutePath) }
            ?: false
    }
    LaunchedEffect(currentFile?.absolutePath) {
        isCurrentImageZoomed = false
        if (showImageEditor) showImageEditor = false
    }

    // ── Slideshow: auto-advance through image pages in the folder ──────
    LaunchedEffect(slideshowActive) {
        if (!slideshowActive) return@LaunchedEffect
        // Build ordered list of actual page indices that are image files
        val imageActualIndices = (0 until actualCount).filter { FileUtils.isImageFile(mediaFiles[it]) }
        if (imageActualIndices.size <= 1) { slideshowActive = false; return@LaunchedEffect }
        while (slideshowActive) {
            delay(3_000L)
            if (!slideshowActive) break
            val currentActual = virtualToActual(pagerState.currentPage)
            val idxInSlideshow = imageActualIndices.indexOf(currentActual).let { if (it < 0) 0 else it }
            val nextActual = imageActualIndices[(idxInSlideshow + 1) % imageActualIndices.size]
            val nextVirtual = if (loopEnabled && actualCount > 1) {
                pagerState.currentPage - currentActual + nextActual
            } else nextActual
            pagerState.animateScrollToPage(nextVirtual)
        }
    }

    // ── Control system bars ─────────────────────────────────────────────
    val view = LocalView.current
    val activity = (view.context as? android.app.Activity)

    LaunchedEffect(isImmersive) {
        activity?.window?.let { window ->
            val controller = WindowCompat.getInsetsController(window, view)
            if (isImmersive) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Restore system bars when leaving
    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // ── UI ──────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 0,   // prevent pre-rendering adjacent pages
            userScrollEnabled = pagerScrollEnabled,
            key = { "${it}_${mediaFiles[virtualToActual(it)].absolutePath}" }
        ) { page ->
            val actualPage = virtualToActual(page)
            val file = mediaFiles[actualPage]
            val isCurrentPage = pagerState.settledPage == page

            when {
                FileUtils.isImageFile(file) -> ImagePage(
                    file = file,
                    isActive = isCurrentPage,
                    resetRequest = imageResetRequest,
                    fitMode = imageFitMode,
                    showHud = showImageHud && !showImageEditor,
                    editRecipe = if (showImageEditor && isCurrentPage) {
                        currentEditRecipe
                    } else {
                        appliedImageEdits[file.absolutePath] ?: ImageEditRecipe()
                    },
                    gesturesEnabled = !(showImageEditor && isCurrentPage),
                    onZoomedStateChanged = { zoomed ->
                        if (isCurrentPage) isCurrentImageZoomed = zoomed
                    },
                    onTap = {
                        if (slideshowActive) slideshowActive = false
                        else isImmersive = !isImmersive
                    }
                )
                FileUtils.isVideoFile(file) -> VideoPage(
                    file = file,
                    isActive = isCurrentPage,
                    musicController = musicController,
                    brightness = screenBrightness,
                    onBrightnessChange = { screenBrightness = it.coerceIn(0f, 1f) },
                    onImmersiveChange = { immersive -> isImmersive = immersive },
                    isInPipMode = isInPipMode,
                    onEnterPip = onEnterPip,
                    onPlayingChanged = onVideoPlayingChanged
                )
                                FileUtils.isAudioFile(file) -> AudioPage(
                                    file = file,
                                    controller = musicController,
                                    isPlaying = isAudioPlaying && isCurrentPage,
                                    currentPosition = if (isCurrentPage) audioPosition else 0L,
                                    duration = if (isCurrentPage) audioDuration else 0L,
                                    repeatMode = repeatMode,
                                    trackIndex = (pageToPlaylistIndex[actualPage] ?: 0) + 1,
                                    totalTracks = audioPageIndices.size,
                                    onToggleRepeat = {
                                        val next = if (repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_ONE
                                        repeatMode = next
                                        musicController?.let { mc ->
                                            try { mc.repeatMode = next } catch (_: Exception) { try { mc.setRepeatMode(next) } catch (_: Exception) {} }
                                        }
                                    },
                                    onPlayPause = {
                                        if (musicController?.isPlaying == true) musicController?.pause()
                                        else playCurrentAudio()
                                    },
                                    onTap = { isImmersive = !isImmersive }
                                )
            }
        }

        // Top bar overlay with animation
        AnimatedVisibility(
            visible = !isImmersive && !isInPipMode && !showImageEditor,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
                TopAppBar(
                title = {
                    if (!isCurrentFileAudio) {
                        Column {
                            Text(
                                text = currentFile?.name ?: "",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${virtualToActual(pagerState.settledPage) + 1} / $actualCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (!isCurrentFileAudio) {
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close"
                            )
                        }
                    }
                },
                actions = {
                    if (isCurrentFileImage) {
                        Box {
                            IconButton(onClick = { showImageMenu = true }) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More options",
                                    tint = Color.White
                                )
                            }
                            DropdownMenu(
                                expanded = showImageMenu,
                                onDismissRequest = { showImageMenu = false }
                            ) {
                                // ── Favorite toggle ──────────────────────────────
                                DropdownMenuItem(
                                    text = { Text(if (isFavorite) "Remove from Favorites" else "Add to Favorites") },
                                    leadingIcon = {
                                        if (isFavorite) {
                                            Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFFF9800))
                                        } else {
                                            Icon(Icons.Filled.FavoriteBorder, contentDescription = null)
                                        }
                                    },
                                    onClick = {
                                        showImageMenu = false
                                        currentFile?.let { file ->
                                            isFavorite = FavoritesManager.toggleFavorite(context, file.absolutePath)
                                        }
                                    }
                                )
                                // ── Share ────────────────────────────────────────
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                    onClick = {
                                        showImageMenu = false
                                        currentFile?.let { file ->
                                            context.startActivity(FileUtils.getShareFileIntent(context, file))
                                        }
                                    }
                                )
                                // ── Delete ───────────────────────────────────────
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                    onClick = {
                                        showImageMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                                // ── Slideshow ────────────────────────────────────
                                DropdownMenuItem(
                                    text = { Text(if (slideshowActive) "Stop Slideshow" else "Slideshow") },
                                    leadingIcon = { Icon(Icons.Filled.Slideshow, contentDescription = null) },
                                    onClick = {
                                        showImageMenu = false
                                        slideshowActive = !slideshowActive
                                    }
                                )
                                // ── Brightness ───────────────────────────────────
                                DropdownMenuItem(
                                    text = { Text("Brightness") },
                                    leadingIcon = { Icon(Icons.Filled.BrightnessHigh, contentDescription = null) },
                                    onClick = {
                                        showImageMenu = false
                                        showBrightnessSheet = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Reset Zoom") },
                                    onClick = {
                                        showImageMenu = false
                                        imageResetRequest++
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Edit") },
                                    onClick = {
                                        showImageMenu = false
                                        val path = currentFile?.absolutePath ?: return@DropdownMenuItem
                                        val base = appliedImageEdits[path] ?: ImageEditRecipe()
                                        editHistory = listOf(base)
                                        editHistoryIndex = 0
                                        imageEditorMode = ImageEditorMode.Full
                                        showImageEditor = true
                                        isImmersive = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Crop Image") },
                                    onClick = {
                                        showImageMenu = false
                                        val path = currentFile?.absolutePath ?: return@DropdownMenuItem
                                        val base = appliedImageEdits[path] ?: ImageEditRecipe()
                                        editHistory = listOf(base)
                                        editHistoryIndex = 0
                                        imageEditorMode = ImageEditorMode.CropOnly
                                        showImageEditor = true
                                        isImmersive = false
                                    }
                                )
                                if (!((appliedImageEdits[currentFile?.absolutePath] ?: ImageEditRecipe()).isIdentity)) {
                                    DropdownMenuItem(
                                        text = { Text("Revert Applied Edits") },
                                        onClick = {
                                            showImageMenu = false
                                            currentFile?.absolutePath?.let { path ->
                                                appliedImageEdits = appliedImageEdits.toMutableMap().also { it.remove(path) }
                                                imageResetRequest++
                                            }
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Fit mode: ${imageFitMode.label}") },
                                    onClick = { showImageMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Image Fit") },
                                    onClick = {
                                        showImageMenu = false
                                        imageFitMode = ImageFitMode.Fit
                                        imageResetRequest++
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Image Fill") },
                                    onClick = {
                                        showImageMenu = false
                                        imageFitMode = ImageFitMode.Fill
                                        imageResetRequest++
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Image Original Size") },
                                    onClick = {
                                        showImageMenu = false
                                        imageFitMode = ImageFitMode.Original
                                        imageResetRequest++
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (showImageHud) "Hide Zoom HUD" else "Show Zoom HUD") },
                                    onClick = {
                                        showImageMenu = false
                                        showImageHud = !showImageHud
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                ),
                modifier = Modifier.systemBarsPadding()
            )
        }

        // ── Slideshow active indicator ──────────────────────────────────────
        AnimatedVisibility(
            visible = slideshowActive,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(bottom = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { slideshowActive = false }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Slideshow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "Slideshow  ·  Tap to stop",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (showImageEditor && currentFile != null && FileUtils.isImageFile(currentFile)) {
            fun pushEdit(next: ImageEditRecipe) {
                editHistory = editHistory.take(editHistoryIndex + 1) + next
                editHistoryIndex = editHistory.lastIndex
            }
            ImageEditorOverlay(
                recipe = currentEditRecipe,
                cropOnly = imageEditorMode == ImageEditorMode.CropOnly,
                canUndo = editHistoryIndex > 0,
                canRedo = editHistoryIndex < editHistory.lastIndex,
                onUndo = { editHistoryIndex = (editHistoryIndex - 1).coerceAtLeast(0) },
                onRedo = { editHistoryIndex = (editHistoryIndex + 1).coerceAtMost(editHistory.lastIndex) },
                onRotateLeft = {
                    pushEdit(currentEditRecipe.copy(rotationQuarterTurns = currentEditRecipe.rotationQuarterTurns - 1))
                },
                onRotateRight = {
                    pushEdit(currentEditRecipe.copy(rotationQuarterTurns = currentEditRecipe.rotationQuarterTurns + 1))
                },
                onFlipHorizontal = {
                    pushEdit(currentEditRecipe.copy(flipHorizontal = !currentEditRecipe.flipHorizontal))
                },
                onFlipVertical = {
                    pushEdit(currentEditRecipe.copy(flipVertical = !currentEditRecipe.flipVertical))
                },
                onCropPresetChange = { preset ->
                    pushEdit(currentEditRecipe.copy(cropPreset = preset))
                },
                onPresetChange = { preset ->
                    pushEdit(currentEditRecipe.withAdjustmentPreset(preset))
                },
                onBrightnessChange = { value ->
                    pushEdit(currentEditRecipe.copy(brightnessAdj = value, adjustmentPreset = ImageAdjustmentPreset.Custom))
                },
                onContrastChange = { value ->
                    pushEdit(currentEditRecipe.copy(contrastAdj = value, adjustmentPreset = ImageAdjustmentPreset.Custom))
                },
                onSaturationChange = { value ->
                    pushEdit(currentEditRecipe.copy(saturationAdj = value, adjustmentPreset = ImageAdjustmentPreset.Custom))
                },
                onWarmthChange = { value ->
                    pushEdit(currentEditRecipe.copy(warmthAdj = value, adjustmentPreset = ImageAdjustmentPreset.Custom))
                },
                onHighlightsChange = { value ->
                    pushEdit(currentEditRecipe.copy(highlightsAdj = value, adjustmentPreset = ImageAdjustmentPreset.Custom))
                },
                onShadowsChange = { value ->
                    pushEdit(currentEditRecipe.copy(shadowsAdj = value, adjustmentPreset = ImageAdjustmentPreset.Custom))
                },
                onCancel = {
                    activeSaveJob?.cancel()
                    activeSaveJob = null
                    showSaveProgress = false
                    showImageEditor = false
                    imageEditorMode = ImageEditorMode.Full
                },
                onApply = {
                    val path = currentFile.absolutePath
                    val nextMap = appliedImageEdits.toMutableMap()
                    if (currentEditRecipe.isIdentity) nextMap.remove(path) else nextMap[path] = currentEditRecipe
                    appliedImageEdits = nextMap
                    showImageEditor = false
                    imageEditorMode = ImageEditorMode.Full
                },
                onSaveCopy = {
                    val fileForSave = currentFile
                    val recipeForSave = currentEditRecipe
                    activeSaveJob = coroutineScope.launch {
                        showSaveProgress = true
                        val result = applyEditRecipeAndSave(context, fileForSave, recipeForSave, saveCopy = true)
                        showSaveProgress = false
                        activeSaveJob = null
                        result.onSuccess { savedFile ->
                            val path = fileForSave.absolutePath
                            val nextMap = appliedImageEdits.toMutableMap()
                            nextMap.remove(path)
                            appliedImageEdits = nextMap
                            showImageEditor = false
                            imageEditorMode = ImageEditorMode.Full
                            onFileAdded(savedFile)
                            saveResultMessage = "Saved as ${savedFile.name}"
                        }.onFailure { err ->
                            saveErrorMessage = classifySaveError(err)
                        }
                    }
                },
                onReplaceOriginal = {
                    val isSmbFile = currentFile.name.startsWith("smb_open_") ||
                        currentFile.name.startsWith("smb_stream_")
                    when {
                        isSmbFile ->
                            saveErrorMessage = "Cannot replace remote files. Use Save Copy instead."
                        !currentFile.canWrite() ->
                            saveErrorMessage = "File is read-only. Use Save Copy instead."
                        else ->
                            showReplaceConfirmDialog = true
                    }
                }
            )
        }

        // ── Save progress indicator ────────────────────────────────────────────
        if (showSaveProgress) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Saving…", color = Color.White, fontSize = 14.sp)
                    TextButton(onClick = {
                        activeSaveJob?.cancel()
                        activeSaveJob = null
                        showSaveProgress = false
                    }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }

        // ── Save result toast overlay ──────────────────────────────────────────
        val msg = saveResultMessage
        if (msg != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .systemBarsPadding()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = msg,
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E1E1E))
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .clickable { saveResultMessage = null }
                )
            }
            LaunchedEffect(msg) {
                delay(3500)
                if (saveResultMessage == msg) saveResultMessage = null
            }
        }
    } // end outer Box

    // ── Replace original confirmation dialog ───────────────────────────────────
    if (showReplaceConfirmDialog && currentFile != null) {
        val fileForReplace = currentFile
        val recipeForReplace = currentEditRecipe
        AlertDialog(
            onDismissRequest = { showReplaceConfirmDialog = false },
            title = { Text("Replace Original?") },
            text = { Text("This will permanently overwrite \"${fileForReplace.name}\" with the edited version. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showReplaceConfirmDialog = false
                    activeSaveJob = coroutineScope.launch {
                        showSaveProgress = true
                        val result = applyEditRecipeAndSave(context, fileForReplace, recipeForReplace, saveCopy = false)
                        showSaveProgress = false
                        activeSaveJob = null
                        result.onSuccess { _ ->
                            val path = fileForReplace.absolutePath
                            val nextMap = appliedImageEdits.toMutableMap()
                            nextMap.remove(path)
                            appliedImageEdits = nextMap
                            showImageEditor = false
                            imageEditorMode = ImageEditorMode.Full
                            // Invalidate Coil memory cache so the replaced image reloads
                            context.imageLoader.memoryCache?.clear()
                            imageResetRequest++
                            saveResultMessage = "Original replaced successfully"
                        }.onFailure { err ->
                            saveErrorMessage = classifySaveError(err)
                        }
                    }
                }) {
                    Text("Replace", color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = { showReplaceConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Save error dialog ──────────────────────────────────────────────────────
    val errMsg = saveErrorMessage
    if (errMsg != null) {
        AlertDialog(
            onDismissRequest = { saveErrorMessage = null },
            title = { Text("Save Failed") },
            text = { Text(errMsg) },
            confirmButton = {
                TextButton(onClick = { saveErrorMessage = null }) { Text("OK") }
            }
        )
    }

    // ── Delete confirmation dialog ──────────────────────────────────────────
    if (showDeleteDialog && currentFile != null) {
        val fileToDelete = currentFile
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Image?") },
            text = { Text("Move \"${fileToDelete.name}\" to the Recycle Bin?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    RecycleBinManager.moveToRecycleBin(context, fileToDelete)
                    onFileDeleted(fileToDelete)
                }) {
                    Text("Delete", color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ── Brightness bottom-sheet ─────────────────────────────────────────────
    if (showBrightnessSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBrightnessSheet = false },
            sheetState = brightnessSheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Brightness",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BrightnessLow,
                        contentDescription = "Low brightness",
                        modifier = Modifier.size(22.dp)
                    )
                    Slider(
                        value = screenBrightness.coerceIn(0f, 1f),
                        onValueChange = { screenBrightness = it.coerceIn(0f, 1f) },
                        valueRange = 0f..1f,
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Filled.BrightnessHigh,
                        contentDescription = "High brightness",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagePage(
    file: File,
    isActive: Boolean,
    resetRequest: Int,
    fitMode: ImageFitMode,
    showHud: Boolean,
    editRecipe: ImageEditRecipe,
    gesturesEnabled: Boolean,
    onZoomedStateChanged: (Boolean) -> Unit,
    onTap: () -> Unit
) {
    var scale by remember(file.absolutePath) { mutableStateOf(1f) }
    var offset by remember(file.absolutePath) { mutableStateOf(Offset.Zero) }
    var containerSize by remember(file.absolutePath) { mutableStateOf(IntSize.Zero) }
    var highQualityDecode by remember(file.absolutePath) { mutableStateOf(false) }

    fun clampedOffset(raw: Offset, currentScale: Float): Offset {
        if (containerSize == IntSize.Zero || currentScale <= 1f) return Offset.Zero
        val maxX = ((containerSize.width * (currentScale - 1f)) / 2f).coerceAtLeast(0f)
        val maxY = ((containerSize.height * (currentScale - 1f)) / 2f).coerceAtLeast(0f)
        return Offset(
            x = raw.x.coerceIn(-maxX, maxX),
            y = raw.y.coerceIn(-maxY, maxY)
        )
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val nextScale = (scale * zoomChange).coerceIn(1f, 6f)
        scale = nextScale
        offset = if (nextScale > 1.01f) {
            clampedOffset(offset + panChange, nextScale)
        } else {
            Offset.Zero
        }
    }

    LaunchedEffect(resetRequest, isActive) {
        if (isActive) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    LaunchedEffect(fitMode, isActive) {
        if (isActive) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    LaunchedEffect(isActive, scale, gesturesEnabled) {
        if (isActive) onZoomedStateChanged(gesturesEnabled && scale > 1.01f)
    }
    LaunchedEffect(file.absolutePath, scale, fitMode, isActive) {
        highQualityDecode = false
        if (!isActive) return@LaunchedEffect
        val shouldSharpen = scale > 1.2f || fitMode == ImageFitMode.Original
        if (shouldSharpen) {
            delay(180)
            highQualityDecode = true
        }
    }
    LaunchedEffect(gesturesEnabled, isActive) {
        if (!gesturesEnabled && isActive) {
            scale = 1f
            offset = Offset.Zero
            onZoomedStateChanged(false)
        }
    }

    val cropRatio = editRecipe.cropPreset.ratio
    val imageColorFilter = editRecipe.toColorFilterOrNull()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { containerSize = it }
            .pointerInput(gesturesEnabled) {
                if (gesturesEnabled) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = {
                            if (scale > 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        }
                    )
                }
            }
            // Keep single-finger horizontal drags available for pager swipes
            // until the user is actually zoomed in.
            .transformable(
                state = transformState,
                enabled = gesturesEnabled,
                canPan = { scale > 1.01f }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = if (cropRatio != null) {
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(cropRatio)
                    .clip(RoundedCornerShape(10.dp))
            } else {
                Modifier.fillMaxSize()
            },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(file)
                    .precision(if (highQualityDecode) Precision.EXACT else Precision.INEXACT)
                    .size(
                        if (highQualityDecode) {
                            CoilSize.ORIGINAL
                        } else {
                            CoilSize(
                                width = containerSize.width.coerceAtLeast(1),
                                height = containerSize.height.coerceAtLeast(1)
                            )
                        }
                    )
                    .crossfade(!highQualityDecode)
                    .build(),
                contentDescription = file.name,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val flipX = if (editRecipe.flipHorizontal) -1f else 1f
                        val flipY = if (editRecipe.flipVertical) -1f else 1f
                        scaleX = scale * flipX
                        scaleY = scale * flipY
                        translationX = offset.x
                        translationY = offset.y
                        rotationZ = editRecipe.normalizedQuarterTurns * 90f
                    },
                contentScale = fitMode.contentScale,
                colorFilter = imageColorFilter
            )
        }
        if (showHud) {
            val zoomText = String.format(Locale.US, "%.1fx", scale)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 20.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = buildString {
                        append(zoomText)
                        append("  ·  ")
                        append(fitMode.label)
                        if (editRecipe.cropPreset != ImageCropPreset.Free) {
                            append("  ·  ")
                            append(editRecipe.cropPreset.label)
                        }
                        if (editRecipe.adjustmentPreset != ImageAdjustmentPreset.Custom) {
                            append("  ·  ")
                            append(editRecipe.adjustmentPreset.label)
                        }
                    },
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
    }

@Composable
private fun ImageEditorOverlay(
    recipe: ImageEditRecipe,
    cropOnly: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRotateLeft: () -> Unit,
    onRotateRight: () -> Unit,
    onFlipHorizontal: () -> Unit,
    onFlipVertical: () -> Unit,
    onCropPresetChange: (ImageCropPreset) -> Unit,
    onPresetChange: (ImageAdjustmentPreset) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onContrastChange: (Float) -> Unit,
    onSaturationChange: (Float) -> Unit,
    onWarmthChange: (Float) -> Unit,
    onHighlightsChange: (Float) -> Unit,
    onShadowsChange: (Float) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onSaveCopy: () -> Unit,
    onReplaceOriginal: () -> Unit
) {
    var showSaveMenu by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Text(
                text = if (cropOnly) "Crop" else "Edit",
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onApply) { Text("Apply") }
                Box {
                    IconButton(onClick = { showSaveMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Save options", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showSaveMenu,
                        onDismissRequest = { showSaveMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Save Copy") },
                            onClick = { showSaveMenu = false; onSaveCopy() }
                        )
                        DropdownMenuItem(
                            text = { Text("Replace Original") },
                            onClick = { showSaveMenu = false; onReplaceOriginal() }
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .systemBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onUndo, enabled = canUndo) { Text("Undo") }
                TextButton(onClick = onRedo, enabled = canRedo) { Text("Redo") }
                TextButton(onClick = onRotateLeft) { Text("Rotate -90°") }
                TextButton(onClick = onRotateRight) { Text("Rotate +90°") }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onFlipHorizontal) {
                    Text(if (recipe.flipHorizontal) "Unflip H" else "Flip H")
                }
                TextButton(onClick = onFlipVertical) {
                    Text(if (recipe.flipVertical) "Unflip V" else "Flip V")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                ImageCropPreset.entries.forEach { preset ->
                    TextButton(
                        onClick = { onCropPresetChange(preset) },
                        enabled = recipe.cropPreset != preset
                    ) { Text(preset.label) }
                }
            }
            if (!cropOnly) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf(
                        ImageAdjustmentPreset.Auto,
                        ImageAdjustmentPreset.BlackWhite,
                        ImageAdjustmentPreset.Vivid,
                        ImageAdjustmentPreset.Soft
                    ).forEach { preset ->
                        TextButton(
                            onClick = { onPresetChange(preset) },
                            enabled = recipe.adjustmentPreset != preset
                        ) { Text(preset.label) }
                    }
                }
                Text(
                    text = "Brightness ${(recipe.brightnessAdj * 100).toInt()}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 12.sp
                )
                Slider(
                    value = recipe.brightnessAdj,
                    onValueChange = onBrightnessChange,
                    valueRange = -0.5f..0.5f
                )
                Text(text = "Contrast ${String.format(Locale.US, "%.2f", recipe.contrastAdj)}", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                Slider(value = recipe.contrastAdj, onValueChange = onContrastChange, valueRange = 0.5f..1.6f)
                Text(text = "Saturation ${String.format(Locale.US, "%.2f", recipe.saturationAdj)}", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                Slider(value = recipe.saturationAdj, onValueChange = onSaturationChange, valueRange = 0f..2f)
                Text(text = "Warmth ${String.format(Locale.US, "%.2f", recipe.warmthAdj)}", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                Slider(value = recipe.warmthAdj, onValueChange = onWarmthChange, valueRange = -1f..1f)
                Text(text = "Highlights ${String.format(Locale.US, "%.2f", recipe.highlightsAdj)}", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                Slider(value = recipe.highlightsAdj, onValueChange = onHighlightsChange, valueRange = -1f..1f)
                Text(text = "Shadows ${String.format(Locale.US, "%.2f", recipe.shadowsAdj)}", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                Slider(value = recipe.shadowsAdj, onValueChange = onShadowsChange, valueRange = -1f..1f)
            }
        }
    }
}
private fun findLocalSubtitleFile(videoFile: File): File? {
    val parent = videoFile.parentFile ?: return null
    val subtitleFiles = parent.listFiles()
        ?.filter { it.isFile }
        ?: return null

    val exactBasename = subtitleFiles.firstOrNull {
        it.extension.equals("srt", ignoreCase = true) &&
            it.nameWithoutExtension.equals(videoFile.nameWithoutExtension, ignoreCase = true)
    }
    if (exactBasename != null) return exactBasename

    val selected = SubtitleSidecarResolver.findBestMatchingSrt(
        videoName = videoFile.name,
        candidates = subtitleFiles,
        nameSelector = { it.name }
    )
    return selected
}

private fun inferVideoMimeType(file: File): String? {
    return FileUtils.getMimeTypeOrNull(file)?.takeIf { it.startsWith("video/") }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPage(
    file: File,
    isActive: Boolean,
    musicController: MediaController?,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    onImmersiveChange: (Boolean) -> Unit,
    isInPipMode: Boolean = false,
    onEnterPip: () -> Unit = {},
    onPlayingChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaUri = remember(file.absolutePath) { android.net.Uri.fromFile(file) }
    val smbDataSourceFactory = remember { SmbSeekableDataSourceFactory(context) }
    val mediaSourceFactory = remember(smbDataSourceFactory) {
        DefaultMediaSourceFactory(smbDataSourceFactory)
    }

    // Use more generous buffer durations for files that may be coming from a
    // network source (SMB).  MKV containers in particular require multiple
    // backward/forward seeks during initial parsing; a small buffer caused
    // ExoPlayer to stall or raise a playback error before the first frame was
    // decoded.  These values are still well within RAM budget for a single
    // video and do not affect local-file performance.
    val isNetworkStream = remember(file.absolutePath) {
        file.absolutePath.contains("smb_stream_") || file.absolutePath.contains("smb_open_")
    }
    val exoPlayer = remember(file.absolutePath) {
        ExoPlayer.Builder(context)
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ if (isNetworkStream) 20_000 else 2_000,
                        /* maxBufferMs = */ if (isNetworkStream) 90_000 else 10_000,
                        /* bufferForPlaybackMs = */ if (isNetworkStream) 4_000 else 300,
                        /* bufferForPlaybackAfterRebufferMs = */ if (isNetworkStream) 8_000 else 700
                    )
                    .build()
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    // Helper: save current position synchronously (commit=true) so the write
    // is guaranteed to reach disk even if the process is killed immediately after.
    fun savePosition() {
        try {
            val prefs = context.getSharedPreferences("media_positions", Context.MODE_PRIVATE)
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration
            if (pos > 0L && (dur <= 0L || pos < dur - 3_000L)) {
                prefs.edit(commit = true) { putLong(file.absolutePath, pos) }
            } else if (pos > 0L) {
                // Near or past end — clear so next open starts fresh
                prefs.edit(commit = true) { remove(file.absolutePath) }
            }
        } catch (_: Exception) {}
    }

    // ── Settings-menu state (hoisted so DisposableEffects can access them) ──
    var currentSpeed by remember { mutableStateOf(1f) }
    var availableAudioTracks by remember { mutableStateOf<List<Triple<Int, Int, String>>>(emptyList()) }
    var selectedAudioGroupIdx by remember { mutableIntStateOf(-1) }
    var selectedAudioTrackIdx by remember { mutableIntStateOf(-1) }

    LaunchedEffect(isActive, mediaUri) {
        if (isActive) {
            if (exoPlayer.currentMediaItem == null) {
                // Read saved position BEFORE prepare() so we can pass it directly
                // to setMediaItem — this is the most reliable way to start ExoPlayer
                // at a specific position (avoids a race between prepare() and seekTo()).
                val startPosition = try {
                    val prefs = context.getSharedPreferences("media_positions", Context.MODE_PRIVATE)
                    prefs.getLong(file.absolutePath, 0L).coerceAtLeast(0L)
                } catch (_: Exception) { 0L }

                val mediaItem = MediaItem.Builder()
                    .setUri(mediaUri)
                    .setMimeType(inferVideoMimeType(file))
                    .apply {
                        val isSmbVideoPlayback =
                            file.name.startsWith("smb_open_") || file.name.startsWith("smb_stream_")
                        if (!isSmbVideoPlayback) {
                            val localSubtitle = findLocalSubtitleFile(file)
                            if (localSubtitle != null) {
                                setSubtitleConfigurations(
                                    listOf(
                                        MediaItem.SubtitleConfiguration.Builder(android.net.Uri.fromFile(localSubtitle))
                                            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                                            .build()
                                    )
                                )
                            }
                        }
                    }
                    .build()

                val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
                exoPlayer.setMediaSource(mediaSource, startPosition)
                exoPlayer.prepare()
                // Reset playback speed for the new file
                exoPlayer.setPlaybackSpeed(1f)
                currentSpeed = 1f
            }
            // Explicitly pause music service when video page becomes active
            musicController?.let { mc ->
                if (mc.isPlaying) mc.pause()
            }
            // Auto-play so resume position takes effect immediately
            exoPlayer.playWhenReady = true
        } else {
            // Page is no longer active — save position and release resources.
            savePosition()
            exoPlayer.pause()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    // Clear stored position when playback naturally completes.
    DisposableEffect(exoPlayer, file.absolutePath) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    try {
                        val prefs = context.getSharedPreferences(
                            "media_positions",
                            Context.MODE_PRIVATE
                        )
                        prefs.edit(commit = true) { remove(file.absolutePath) }
                    } catch (_: Exception) {}
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // ── Audio-track memory: restore the last selected audio track when the
    //    video becomes ready, and save it whenever the user picks a new one. ──
    DisposableEffect(exoPlayer, file.absolutePath) {
        // Flag: set to true after we have applied the initial restore so that
        // subsequent onTracksChanged calls (user-initiated) are saved, but the
        // very first onChange caused by the restore itself isn't double-saved
        // before we even finish setting up.
        var audioTrackRestored = false

        /** Persist the currently selected audio group/track index for this file. */
        fun saveAudioTrack() {
            try {
                val tracks = exoPlayer.currentTracks
                var audioGroupIdx = 0
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        for (trackIdx in 0 until group.length) {
                            if (group.isTrackSelected(trackIdx)) {
                                context.getSharedPreferences(
                                    "video_audio_tracks", Context.MODE_PRIVATE
                                ).edit { putString(file.absolutePath, "$audioGroupIdx:$trackIdx") }
                                return
                            }
                        }
                        audioGroupIdx++
                    }
                }
            } catch (_: Exception) {}
        }

        /** Apply the previously saved audio track selection (if any). */
        fun restoreAudioTrack() {
            try {
                val saved = context.getSharedPreferences(
                    "video_audio_tracks", Context.MODE_PRIVATE
                ).getString(file.absolutePath, null) ?: return
                val parts = saved.split(":")
                if (parts.size != 2) return
                val savedGroupIdx = parts[0].toIntOrNull() ?: return
                val savedTrackIdx = parts[1].toIntOrNull() ?: return

                val tracks = exoPlayer.currentTracks
                var audioGroupIdx = 0
                for (group in tracks.groups) {
                    if (group.type == C.TRACK_TYPE_AUDIO) {
                        if (audioGroupIdx == savedGroupIdx && savedTrackIdx < group.length) {
                            // Only override if the saved track is not already selected
                            if (!group.isTrackSelected(savedTrackIdx)) {
                                exoPlayer.trackSelectionParameters =
                                    exoPlayer.trackSelectionParameters
                                        .buildUpon()
                                        .setOverrideForType(
                                            TrackSelectionOverride(
                                                group.mediaTrackGroup,
                                                savedTrackIdx
                                            )
                                        )
                                        .build()
                            }
                            return
                        }
                        audioGroupIdx++
                    }
                }
            } catch (_: Exception) {}
        }

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                // Restore the saved audio track exactly once, as soon as tracks
                // are available (STATE_READY).
                if (state == Player.STATE_READY && !audioTrackRestored) {
                    restoreAudioTrack()
                    audioTrackRestored = true
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                // Save the selected audio track whenever it changes, but only
                // AFTER the initial restore so we don't overwrite a saved
                // preference with the default before restoring it.
                if (audioTrackRestored) {
                    saveAudioTrack()
                }
            }
        }

        exoPlayer.addListener(listener)
        onDispose { exoPlayer.removeListener(listener) }
    }

    // Periodically persist the current playback position while the page is
    // active, so the resume position survives process death.
    LaunchedEffect(exoPlayer, file.absolutePath, isActive) {
        if (!isActive) return@LaunchedEffect
        while (true) {
            delay(3_000)
            savePosition()
        }
    }

    // Save position on every ON_PAUSE (guaranteed before the process can be killed).
    // This is the most reliable save point and uses a synchronous commit.
    val lifecycleOwnerForSave = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwnerForSave, file.absolutePath) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && isActive) {
                savePosition()
            }
        }
        lifecycleOwnerForSave.lifecycle.addObserver(observer)
        onDispose { lifecycleOwnerForSave.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(file.absolutePath) {
        onDispose {
            // Synchronous commit so the write reaches disk before the process dies.
            savePosition()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.release()
        }
    }

    // ── Screen wake lock: keep screen on while playing, allow timeout when paused ──
    // Uses view.keepScreenOn which maps to FLAG_KEEP_SCREEN_ON on the window.
    // When the flag is cleared the device's normal screen-off timer resumes.
    val videoView = LocalView.current
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                videoView.keepScreenOn = isPlaying
            }
        }
        exoPlayer.addListener(listener)
        // Sync immediately with current player state (e.g. composable recomposed
        // while already playing).
        videoView.keepScreenOn = exoPlayer.isPlaying
        onDispose {
            exoPlayer.removeListener(listener)
            // Always clear on disposal so the screen-off timer is restored.
            videoView.keepScreenOn = false
        }
    }

        // ── Track available audio tracks for the settings menu ──────────────
        DisposableEffect(exoPlayer, file.absolutePath) {
            val listener = object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    val newTracks = mutableListOf<Triple<Int, Int, String>>()
                    var groupIdx = 0
                    var selGroup = -1
                    var selTrack = -1
                    for (group in tracks.groups) {
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            for (trackIdx in 0 until group.length) {
                                val format = group.getTrackFormat(trackIdx)
                                val lang = format.language
                                val lbl = format.label
                                val displayName = when {
                                    !lang.isNullOrBlank() && !lbl.isNullOrBlank() -> "$lang – $lbl"
                                    !lang.isNullOrBlank() -> lang
                                    !lbl.isNullOrBlank() -> lbl
                                    else -> "Track ${newTracks.size + 1}"
                                }
                                newTracks.add(Triple(groupIdx, trackIdx, displayName))
                                if (group.isTrackSelected(trackIdx)) {
                                    selGroup = groupIdx
                                    selTrack = trackIdx
                                }
                            }
                            groupIdx++
                        }
                    }
                    availableAudioTracks = newTracks
                    if (selGroup >= 0) {
                        selectedAudioGroupIdx = selGroup
                        selectedAudioTrackIdx = selTrack
                    }
                }
            }
            exoPlayer.addListener(listener)
            onDispose { exoPlayer.removeListener(listener) }
        }

    // ── PiP play/pause: expose the active exoPlayer directly so the broadcast
    //    receiver can call pause()/play() without going through Compose state.
    DisposableEffect(exoPlayer, isActive) {
        if (isActive) {
            MediaViewerActivity.activePipPlayer = exoPlayer
        }
        onDispose {
            // Only clear if we set it (prevents a later inactive page from
            // clearing the reference set by the newly-active page).
            if (MediaViewerActivity.activePipPlayer === exoPlayer) {
                MediaViewerActivity.activePipPlayer = null
            }
        }
    }

    // ── Report playing state to the Activity so the PiP button icon stays in sync ──
    // Only the active page reports — non-active pages have their player stopped/cleared.
    DisposableEffect(exoPlayer, isActive) {
        if (!isActive) return@DisposableEffect onDispose { }
        val playingListener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                onPlayingChanged(playing)
            }
        }
        exoPlayer.addListener(playingListener)
        onPlayingChanged(exoPlayer.isPlaying)   // sync current state immediately
        onDispose { exoPlayer.removeListener(playingListener) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        var controlsVisible by remember { mutableStateOf(true) }
        var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
        var widthPx by remember { mutableIntStateOf(0) }
        var speedBoostActive by remember { mutableStateOf(false) }

        // ── Background playback toggle (configured in Settings screen) ──
        val backgroundPlaybackEnabled = remember(context) {
            com.gmail.omkarjoshi1989.util.SettingsManager
                .isBackgroundPlaybackEnabled(context)
        }
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner, backgroundPlaybackEnabled) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP && !backgroundPlaybackEnabled) {
                    try { exoPlayer.pause() } catch (_: Exception) {}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        // ── Hoisted volume state ────────────────────────────────────────
        val audioManager = remember(context) {
            context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        }
        val maxVolume = remember(audioManager) {
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        }
        var rawVolume by remember(audioManager) {
            mutableIntStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        }
        DisposableEffect(audioManager) {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    rawVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                }
            }
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI,
                true,
                observer
            )
            onDispose {
                context.contentResolver.unregisterContentObserver(observer)
            }
        }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 3000
                    controllerHideOnTouch = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowFastForwardButton(true)
                    setShowRewindButton(true)
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            val visible = visibility == android.view.View.VISIBLE
                            controlsVisible = visible
                            onImmersiveChange(!visible)
                        }
                    )
                    playerViewRef = this
                }
            },
            update = { playerView ->
                playerView.player = exoPlayer
                playerViewRef = playerView
                // ── Inject PiP into ExoPlayer's existing gear/settings popup ──────────
                // We find the built-in settings button and replace its click listener with
                // one that shows a native PopupMenu containing PiP, speed, and audio options.
                try {
                    val settingsBtn = playerView.findViewById<android.view.View>(
                        androidx.media3.ui.R.id.exo_settings
                    )
                    settingsBtn?.setOnClickListener { anchorView ->
                        val popup = android.widget.PopupMenu(anchorView.context, anchorView)

                        // ── Picture in Picture ────────────────────────────────────────
                        popup.menu.add(0, 0, 0, "Picture in Picture")

                        // ── Share ─────────────────────────────────────────────────────
                        popup.menu.add(0, 500, 1, "Share")

                        // ── Playback Speed ────────────────────────────────────────────
                        popup.menu.add(1, 1, 2, "— Playback Speed —").also { it.isEnabled = false }
                        val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
                        popup.menu.setGroupCheckable(2, true, true)
                        speeds.forEachIndexed { idx, speed ->
                            val lbl = if (speed == 1f) "Normal (1×)" else "${speed}×"
                            popup.menu.add(2, 100 + idx, 10 + idx, lbl).also { item ->
                                item.isCheckable = true
                                item.isChecked = (speed == currentSpeed)
                            }
                        }

                        // ── Audio Tracks (only if multiple tracks exist) ───────────────
                        val trackList = availableAudioTracks
                        if (trackList.size > 1) {
                            popup.menu.add(3, 200, 30, "— Audio Track —").also { it.isEnabled = false }
                            popup.menu.setGroupCheckable(4, true, true)
                            trackList.forEachIndexed { idx, triple ->
                                popup.menu.add(4, 300 + idx, 40 + idx, triple.third).also { item ->
                                    item.isCheckable = true
                                    item.isChecked = (triple.first == selectedAudioGroupIdx &&
                                            triple.second == selectedAudioTrackIdx)
                                }
                            }
                        }

                        popup.setOnMenuItemClickListener { item ->
                            when {
                                item.itemId == 0 -> {
                                    onEnterPip()
                                    true
                                }
                                item.itemId == 500 -> {
                                    try {
                                        anchorView.context.startActivity(
                                            FileUtils.getShareFileIntent(anchorView.context, file)
                                        )
                                    } catch (_: Exception) {}
                                    true
                                }
                                item.itemId in 100..105 -> {
                                    val speed = speeds[item.itemId - 100]
                                    currentSpeed = speed
                                    try { exoPlayer.setPlaybackSpeed(speed) } catch (_: Exception) {}
                                    true
                                }
                                item.itemId in 300..399 -> {
                                    val info = trackList.getOrNull(item.itemId - 300)
                                    if (info != null) {
                                        try {
                                            val tracks = exoPlayer.currentTracks
                                            var cnt = 0
                                            for (group in tracks.groups) {
                                                if (group.type == C.TRACK_TYPE_AUDIO) {
                                                    if (cnt == info.first) {
                                                        exoPlayer.trackSelectionParameters =
                                                            exoPlayer.trackSelectionParameters
                                                                .buildUpon()
                                                                .setOverrideForType(
                                                                    TrackSelectionOverride(
                                                                        group.mediaTrackGroup,
                                                                        info.second
                                                                    )
                                                                ).build()
                                                        selectedAudioGroupIdx = info.first
                                                        selectedAudioTrackIdx = info.second
                                                        break
                                                    }
                                                    cnt++
                                                }
                                            }
                                        } catch (_: Exception) {}
                                    }
                                    true
                                }
                                else -> false
                            }
                        }
                        popup.show()
                    }
                } catch (_: Exception) {}
            },
            modifier = Modifier.fillMaxSize()
        )

        // Hide the ExoPlayer built-in controller while in PiP (it is too small to use).
        // Restore auto-show behaviour when returning to full-screen.
        LaunchedEffect(isInPipMode) {
            val pv = playerViewRef
            if (pv != null) {
                if (isInPipMode) {
                    pv.hideController()
                    pv.controllerAutoShow = false
                } else {
                    pv.controllerAutoShow = true
                }
            }
        }

        // Tap overlay (active only while controller is hidden): single tap
        // re-shows the controller; double-tap on the left half rewinds 10s,
        // on the right half fast-forwards 10s. When the controller IS
        // visible, this Box is a passive layer so taps on play/pause/seek
        // inside PlayerView are not intercepted.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { widthPx = it.width }
                .then(
                    if (!controlsVisible) {
                        Modifier.pointerInput(exoPlayer) {
                            var boostActive = false
                            detectTapGestures(
                                onPress = {
                                    tryAwaitRelease()
                                    if (boostActive) {
                                        try { exoPlayer.setPlaybackSpeed(1f) } catch (_: Exception) {}
                                        boostActive = false
                                        speedBoostActive = false
                                    }
                                },
                                onLongPress = {
                                    if (exoPlayer.isPlaying) {
                                        try { exoPlayer.setPlaybackSpeed(1.5f) } catch (_: Exception) {}
                                        boostActive = true
                                        speedBoostActive = true
                                    }
                                },
                                onTap = {
                                    playerViewRef?.showController()
                                },
                                onDoubleTap = { offset ->
                                    if (widthPx > 0) {
                                        val isLeft = offset.x < widthPx / 2f
                                        val duration = exoPlayer.duration
                                        val current = exoPlayer.currentPosition
                                        val target = if (isLeft) {
                                            (current - 10_000L).coerceAtLeast(0L)
                                        } else {
                                            val upper = if (duration > 0) duration else Long.MAX_VALUE
                                            (current + 10_000L).coerceAtMost(upper)
                                        }
                                        exoPlayer.seekTo(target)
                                    }
                                }
                            )
                        }
                    } else Modifier
                )
        )

        AnimatedVisibility(
            visible = controlsVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Brightness + Volume sliders on the right edge
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VerticalSliderColumn(
                        icon = {
                            Icon(
                                imageVector = Icons.Filled.BrightnessHigh,
                                contentDescription = "Brightness",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        value = brightness,
                        onValueChange = { onBrightnessChange(it) }
                    )
                    VerticalSliderColumn(
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Volume",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        value = rawVolume.toFloat() / maxVolume.toFloat(),
                        onValueChange = { f ->
                            val newVolume = (f * maxVolume).toInt().coerceIn(0, maxVolume)
                            if (newVolume != rawVolume) {
                                rawVolume = newVolume
                                try {
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        newVolume,
                                        0
                                    )
                                } catch (_: SecurityException) {}
                            }
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = speedBoostActive,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .systemBarsPadding()
                .padding(top = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "1.5x",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VerticalSliderColumn(
    icon: @Composable () -> Unit,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(48.dp)
            .pointerInput(Unit) { },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon()
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier.size(width = 48.dp, height = 220.dp),
            contentAlignment = Alignment.Center
        ) {
            Slider(
                value = value.coerceIn(0f, 1f),
                onValueChange = onValueChange,
                valueRange = 0f..1f,
                thumb = {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                },
                modifier = Modifier
                    .graphicsLayer { rotationZ = -90f }
                    .requiredSize(width = 220.dp, height = 48.dp)
            )
        }
    }
}

/**
 * Pure UI component — does NOT connect to the service itself.
 * All playback state is hoisted to [MediaViewerScreen].
 */
@Composable
private fun AudioPage(
    file: File,
    controller: MediaController?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    repeatMode: Int,
    trackIndex: Int,
    totalTracks: Int,
    onToggleRepeat: () -> Unit,
    onPlayPause: () -> Unit,
    onTap: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Album art loaded once per file
    val albumArt by produceState<Bitmap?>(initialValue = null, key1 = file.absolutePath) {
        value = try {
            withContext(Dispatchers.IO) { loadAlbumArt(file) }
        } catch (_: Exception) {
            null
        }
    }

    // Seek state hoisted so both portrait and landscape can share it
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableLongStateOf(currentPosition) }
    val progressFraction = if (duration > 0) {
        if (isSeeking) seekPosition.toFloat() / duration.toFloat()
        else currentPosition.toFloat() / duration.toFloat()
    } else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onTap() },
        contentAlignment = Alignment.Center
    ) {
        if (isLandscape) {
            // ── Landscape layout: album art on left half, controls on right half ──
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left half — album art
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AudioAlbumArt(albumArt = albumArt, landscapeMode = true)
                }

                // Right half — all controls
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .padding(start = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Folder name + track serial
                    Text(
                        text = "${file.parentFile?.name ?: ""}  ·  $trackIndex / $totalTracks",
                        color = Color(0xFFFF9800),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // File name
                    Text(
                        text = stripNumericPrefix(file.nameWithoutExtension),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Seek bar
                    Slider(
                        value = progressFraction,
                        onValueChange = { fraction ->
                            if (duration > 0) {
                                isSeeking = true
                                seekPosition = (fraction * duration).toLong()
                            }
                        },
                        onValueChangeFinished = {
                            controller?.let { mc ->
                                if (duration > 0) {
                                    try { mc.seekTo(seekPosition) } catch (_: Exception) {
                                        try { mc.seekToDefaultPosition() } catch (_: Exception) {}
                                    }
                                }
                            }
                            isSeeking = false
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        valueRange = 0f..1f
                    )

                    // Time labels
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(currentPosition),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                        Text(
                            text = formatTime(duration),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                                    // Playback controls row
                                    AudioControlsRow(
                                        isPlaying = isPlaying,
                                        repeatMode = repeatMode,
                                        onToggleRepeat = onToggleRepeat,
                                        onPlayPause = onPlayPause,
                                        controller = controller
                                    )
                                }
                            }
                        } else {
                            // ── Portrait layout: original vertical column ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                AudioAlbumArt(albumArt = albumArt, landscapeMode = false)

                Spacer(modifier = Modifier.height(24.dp))

                // Folder name + track serial
                Text(
                    text = "${file.parentFile?.name ?: ""}  ·  $trackIndex / $totalTracks",
                    color = Color(0xFFFF9800),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // File name
                Text(
                    text = stripNumericPrefix(file.nameWithoutExtension),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )


                Spacer(modifier = Modifier.height(32.dp))

                // Seek bar
                Slider(
                    value = progressFraction,
                    onValueChange = { fraction ->
                        if (duration > 0) {
                            isSeeking = true
                            seekPosition = (fraction * duration).toLong()
                        }
                    },
                    onValueChangeFinished = {
                        controller?.let { mc ->
                            if (duration > 0) {
                                try { mc.seekTo(seekPosition) } catch (_: Exception) {
                                    try { mc.seekToDefaultPosition() } catch (_: Exception) {}
                                }
                            }
                        }
                        isSeeking = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    valueRange = 0f..1f
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Time labels
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPosition),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                    Text(
                        text = formatTime(duration),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Playback controls row
                AudioControlsRow(
                    isPlaying = isPlaying,
                    repeatMode = repeatMode,
                    onToggleRepeat = onToggleRepeat,
                    onPlayPause = onPlayPause,
                    controller = controller
                )
            }
        }
    }
}

/** Album art image or fallback music-note icon. */
@Composable
private fun AudioAlbumArt(albumArt: Bitmap?, landscapeMode: Boolean) {
    if (albumArt != null) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(albumArt)
                .crossfade(true)
                .build(),
            contentDescription = "Album art",
            modifier = if (landscapeMode) {
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
            } else {
                Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(12.dp))
            },
            contentScale = ContentScale.Fit
        )
    } else {
        // Fallback audio icon when no art is embedded
        val iconSize = if (landscapeMode) 96.dp else 64.dp
        val boxSize = if (landscapeMode) 160.dp else 120.dp
        Box(
            modifier = Modifier
                .size(boxSize)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFFF9800).copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = "Audio",
                modifier = Modifier.size(iconSize),
                tint = Color(0xFFFF9800)
            )
        }
    }
}

/** Previous · Play/Pause · Next controls row with Repeat button below, all centered. */
@Composable
private fun AudioControlsRow(
    isPlaying: Boolean,
    repeatMode: Int,
    onToggleRepeat: () -> Unit,
    onPlayPause: () -> Unit,
    controller: MediaController?
) {
    val repeatActive = repeatMode == Player.REPEAT_MODE_ONE

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Prev · Play/Pause · Next ────────────────────────────────────
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Previous track
            IconButton(
                onClick = {
                    controller?.let { mc ->
                        try { mc.seekToPrevious() } catch (_: Exception) {}
                    }
                },
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }

            // Play / Pause (primary, larger)
            IconButton(
                onClick = { onPlayPause() },
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF9800))
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(42.dp),
                    tint = Color.White
                )
            }

            // Next track
            IconButton(
                onClick = {
                    controller?.let { mc ->
                        try { mc.seekToNext() } catch (_: Exception) {}
                    }
                },
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(36.dp),
                    tint = Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Repeat toggle (centered below play/pause) ────────────────────
        IconButton(
            onClick = { onToggleRepeat() },
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (repeatActive) Color(0xFFFF9800).copy(alpha = 0.25f)
                    else Color.White.copy(alpha = 0.12f)
                )
        ) {
            Icon(
                imageVector = if (repeatActive) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                contentDescription = "Repeat",
                modifier = Modifier.size(28.dp),
                tint = if (repeatActive) Color(0xFFFF9800) else Color.White
            )
        }
    }
}

/**
 * Strips leading numeric prefixes (e.g. "01 02 ", "01. ", "1 - ") from audio file names.
 */
private fun stripNumericPrefix(name: String): String {
    return name.replace(Regex("^(\\d+[\\s._\\-]*)+"), "").trim()
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun loadAlbumArt(file: File): Bitmap? {
    val mmr = MediaMetadataRetriever()
    return try {
        mmr.setDataSource(file.absolutePath)
        val art = mmr.embeddedPicture
        if (art != null) BitmapFactory.decodeByteArray(art, 0, art.size) else null
    } catch (_: Exception) {
        null
    } finally {
        try {
            mmr.release()
        } catch (_: Exception) {
        }
    }
}
