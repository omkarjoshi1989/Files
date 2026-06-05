package com.gmail.omkarjoshi1989.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer as NativePdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

// ── Open document holder ─────────────────────────────────────────────────────
//
// RENDERING STRATEGY (all paths use the native PDFium engine):
//   • Normal PDF        → open directly with NativePdfRenderer.
//   • Password PDF      → PDFBox decrypts in-memory → saves an unencrypted
//                         copy to cacheDir → NativePdfRenderer opens the copy.
//
// This gives perfect rendering quality for ALL fonts and scripts, including
// Devanagari, Arabic, CJK, complex layouts, embedded images, etc.

private class OpenPdfDocument(
    val pfd: ParcelFileDescriptor,
    val renderer: NativePdfRenderer,
    val pageCount: Int,
    /** Non-null when we created a temporary decrypted copy – deleted on close. */
    private val tempFile: File? = null
) {
    fun close() {
        runCatching { renderer.close() }
        runCatching { pfd.close() }
        runCatching { tempFile?.delete() }
    }
}

// ── Convenience overload for a plain File ───────────────────────────────────

@Composable
fun PdfViewerScreen(file: File, onClose: () -> Unit) {
    PdfViewerScreen(
        displayName = file.name,
        openPfd = { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) },
        onClose = onClose
    )
}

// ── Main composable ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    displayName: String,
    openPfd: () -> ParcelFileDescriptor?,
    onClose: () -> Unit
) {
    val context = LocalContext.current

    // ── Core state ───────────────────────────────────────────────────────
    var loadTrigger       by remember { mutableIntStateOf(0) }
    var submittedPassword by remember { mutableStateOf<String?>(null) }
    var isLoaded          by remember { mutableStateOf(false) }
    var isPasswordError   by remember { mutableStateOf(false) }
    var isPasswordProtected by remember { mutableStateOf(false) }
    var isError           by remember { mutableStateOf(false) }
    var openDoc           by remember { mutableStateOf<OpenPdfDocument?>(null) }
    /** NativePdfRenderer must not be called concurrently — serialise via this mutex. */
    val renderMutex = remember { Mutex() }
    var showPasswordDialog by remember { mutableStateOf(false) }

    // ── Immersive / system-bar state ─────────────────────────────────────
    var isImmersive by remember { mutableStateOf(false) }
    val view     = LocalView.current
    val activity = view.context as? android.app.Activity

    LaunchedEffect(isImmersive) {
        activity?.window?.let { window ->
            val ctrl = WindowCompat.getInsetsController(window, view)
            if (isImmersive) {
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
                ctrl.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
            openDoc?.close()
        }
    }

    // ── Load / reload document ───────────────────────────────────────────
    LaunchedEffect(loadTrigger) {
        isLoaded = false
        isError  = false

        withContext(Dispatchers.IO) {

            if (submittedPassword == null) {
                // ── PATH A: no password – open directly with native renderer ──
                val pfd = openPfd()
                if (pfd == null) {
                    withContext(Dispatchers.Main) { isError = true }
                    return@withContext
                }
                try {
                    val native = NativePdfRenderer(pfd)
                    val holder = OpenPdfDocument(pfd, native, native.pageCount)
                    withContext(Dispatchers.Main) {
                        openDoc?.close()
                        openDoc = holder
                        isPasswordProtected = false
                        isPasswordError     = false
                        isLoaded            = true
                    }
                } catch (e: SecurityException) {
                    // Encrypted PDF – prompt for password
                    runCatching { pfd.close() }
                    withContext(Dispatchers.Main) {
                        isPasswordProtected = true
                        isPasswordError     = false
                        showPasswordDialog  = true
                    }
                } catch (_: Exception) {
                    runCatching { pfd.close() }
                    withContext(Dispatchers.Main) { isError = true }
                }

            } else {
                // ── PATH B: password supplied ─────────────────────────────
                //
                // 1. PDFBox opens+decrypts the PDF in memory (the ONLY thing
                //    PDFBox is used for here).
                // 2. We strip the encryption and save an unencrypted copy to
                //    a temp file in cacheDir.
                // 3. NativePdfRenderer (PDFium) opens the temp file →
                //    perfect rendering quality for all fonts and scripts.
                //
                val inputPfd = openPfd()
                var pdfBoxDoc: PDDocument? = null
                try {
                    // Step 1: decrypt
                    ParcelFileDescriptor.AutoCloseInputStream(inputPfd).use { stream ->
                        pdfBoxDoc = PDDocument.load(stream, submittedPassword)
                    }
                    val doc = pdfBoxDoc!!

                    // Step 2: remove encryption and write unencrypted temp file
                    doc.setAllSecurityToBeRemoved(true)
                    val tempFile = File(
                        context.cacheDir,
                        "pdf_unlocked_${System.currentTimeMillis()}.pdf"
                    )
                    doc.save(tempFile)
                    doc.close()
                    pdfBoxDoc = null

                    // Step 3: open temp file with native renderer
                    val tempPfd = ParcelFileDescriptor.open(
                        tempFile, ParcelFileDescriptor.MODE_READ_ONLY
                    )
                    val native = NativePdfRenderer(tempPfd)
                    val holder = OpenPdfDocument(tempPfd, native, native.pageCount, tempFile)

                    withContext(Dispatchers.Main) {
                        openDoc?.close()
                        openDoc = holder
                        isPasswordProtected = true
                        isPasswordError     = false
                        isLoaded            = true
                    }
                } catch (e: InvalidPasswordException) {
                    pdfBoxDoc?.close()
                    withContext(Dispatchers.Main) {
                        isPasswordError    = true
                        showPasswordDialog = true
                    }
                } catch (_: Exception) {
                    pdfBoxDoc?.close()
                    withContext(Dispatchers.Main) { isError = true }
                }
            }
        }
    }

    // ── Password dialog ──────────────────────────────────────────────────
    if (showPasswordDialog) {
        PdfPasswordDialog(
            fileName        = displayName,
            isWrongPassword = isPasswordError,
            onConfirm = { enteredPassword ->
                showPasswordDialog = false
                isPasswordError    = false
                submittedPassword  = enteredPassword
                loadTrigger++
            },
            onDismiss = {
                showPasswordDialog = false
                onClose()
            }
        )
    }

    // ── Main UI ──────────────────────────────────────────────────────────
    val pageCount = if (isLoaded) openDoc?.pageCount ?: 0 else 0

    val statusBarPadding  = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding     = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topContentPadding = statusBarPadding + 64.dp   // 64 dp = Material3 TopAppBar height

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF424242))
    ) {
        when {
            isError -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text  = "Could not open PDF",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            isLoaded && pageCount > 0 -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top    = topContentPadding,
                        bottom = navBarPadding + 8.dp,
                        start  = 8.dp,
                        end    = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pageCount) { pageIndex ->
                        PdfPageItem(
                            displayName       = displayName,
                            pageIndex         = pageIndex,
                            confirmedPassword = submittedPassword,
                            openDoc           = openDoc,
                            renderMutex       = renderMutex,
                            onTap             = { isImmersive = !isImmersive }
                        )
                    }
                }
            }

            else -> {
                if (!showPasswordDialog) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color    = Color.White
                    )
                }
            }
        }

        // ── Top bar overlay ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = !isImmersive,
            enter   = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit    = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isPasswordProtected && isLoaded) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Password protected",
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                text       = displayName,
                                maxLines   = 1,
                                overflow   = TextOverflow.Ellipsis,
                                style      = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        if (pageCount > 0) {
                            Text(
                                text  = if (pageCount == 1) "1 page" else "$pageCount pages",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = Color.Transparent,
                    scrolledContainerColor     = Color.Transparent,
                    titleContentColor          = Color.White,
                    navigationIconContentColor = Color.White
                ),
                modifier = Modifier.systemBarsPadding()
            )
        }
    }
}

// ── Password dialog ─────────────────────────────────────────────────────────

@Composable
private fun PdfPasswordDialog(
    fileName: String,
    isWrongPassword: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password     by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon  = { Icon(Icons.Default.Lock, contentDescription = null) },
        title = { Text("Password Required") },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text  = "\"$fileName\" is password protected.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (isWrongPassword) {
                    Text(
                        text  = "Incorrect password. Please try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value         = password,
                    onValueChange = { password = it },
                    label         = { Text("Password") },
                    singleLine    = true,
                    isError       = isWrongPassword,
                    visualTransformation = if (showPassword)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction    = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (password.isNotEmpty()) onConfirm(password) }
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword)
                                    Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "Hide" else "Show"
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick  = { if (password.isNotEmpty()) onConfirm(password) },
                enabled  = password.isNotEmpty()
            ) { Text("Open") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// ── Per-page composable ─────────────────────────────────────────────────────

@Composable
private fun PdfPageItem(
    displayName: String,
    pageIndex: Int,
    confirmedPassword: String?,
    openDoc: OpenPdfDocument?,
    renderMutex: Mutex,
    onTap: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                indication        = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onTap() }
    ) {
        val targetWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val docKey        = "$displayName|${confirmedPassword ?: ""}"

        val bitmap by produceState<Bitmap?>(
            initialValue = null,
            key1 = docKey,
            key2 = pageIndex,
            key3 = targetWidthPx
        ) {
            value = withContext(Dispatchers.IO) {
                val doc = openDoc ?: return@withContext null
                renderMutex.withLock { renderPage(doc, pageIndex, targetWidthPx) }
            }
        }

        if (bitmap != null) {
            Image(
                bitmap             = bitmap!!.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier           = Modifier.fillMaxWidth(),
                contentScale       = ContentScale.FillWidth
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

// ── Rendering ────────────────────────────────────────────────────────────────

/**
 * Renders [pageIndex] from [doc] into a [Bitmap] scaled to [targetWidthPx].
 * Uses Android's native PDFium engine — perfect quality for all fonts/scripts.
 * Must be called from a background thread and serialised via [renderMutex].
 */
private fun renderPage(doc: OpenPdfDocument, pageIndex: Int, targetWidthPx: Int): Bitmap? {
    if (targetWidthPx <= 0) return null
    return try {
        val page           = doc.renderer.openPage(pageIndex)
        val pageWidthPts   = page.width.coerceAtLeast(1)
        val pageHeightPts  = page.height.coerceAtLeast(1)
        val scale          = targetWidthPx.toFloat() / pageWidthPts
        val targetHeightPx = (pageHeightPts * scale).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(targetWidthPx, targetHeightPx, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawColor(AndroidColor.WHITE)
        page.render(bitmap, null, null, NativePdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        bitmap
    } catch (_: Exception) { null }
}

