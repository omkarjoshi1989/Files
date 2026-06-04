package com.gmail.omkarjoshi1989.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gmail.omkarjoshi1989.util.HiddenAppsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Data
// ---------------------------------------------------------------------------

data class InstalledAppInfo(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean
)

// Keep old ApkInfo so nothing else breaks if referenced elsewhere
data class ApkInfo(
    val file: java.io.File,
    val packageName: String?,
    val appLabel: String?,
    val versionName: String?
)

// ---------------------------------------------------------------------------
// Main Screen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ApplicationsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var allApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var hiddenPackages by remember { mutableStateOf(HiddenAppsManager.getHiddenApps(context)) }
    // Incremented on every ON_RESUME so the list refreshes after an uninstall dialog closes
    var refreshKey by remember { mutableIntStateOf(0) }

    // Observe lifecycle to reload the app list when returning from the system uninstall dialog
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(refreshKey) {
        isLoading = true
        allApps = withContext(Dispatchers.IO) { loadInstalledApps(context) }
        hiddenPackages = HiddenAppsManager.getHiddenApps(context)
        isLoading = false
    }

    fun matches(app: InstalledAppInfo): Boolean =
        searchQuery.isBlank() ||
                app.label.contains(searchQuery, ignoreCase = true)

    // All openable apps that are NOT hidden — user + system combined
    val openableApps = remember(allApps, hiddenPackages, searchQuery) {
        allApps
            .filter { it.packageName !in hiddenPackages && matches(it) }
            .sortedBy { it.label }
    }
    // All apps that were explicitly hidden (both user and system apps)
    val hiddenApps = remember(allApps, hiddenPackages, searchQuery) {
        allApps
            .filter { it.packageName in hiddenPackages && matches(it) }
            .sortedBy { it.label }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Applications", fontWeight = FontWeight.Bold)
                        if (!isLoading) {
                            Text(
                                "${allApps.size} openable app${if (allApps.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Loading apps…", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // ── Search bar (full width) ─────────────────────────────────
                item(span = { GridItemSpan(7) }) {
                    AppsSearchBar(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                }

                // ── Section 1 : All Openable Apps ──────────────────────────
                item(span = { GridItemSpan(7) }) {
                    AppsSectionHeader(
                        title = "Apps", count = openableApps.size,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (openableApps.isEmpty()) {
                    item(span = { GridItemSpan(7) }) {
                        EmptySectionHint(
                            if (searchQuery.isBlank()) "No openable apps found"
                            else "No apps match \"$searchQuery\""
                        )
                    }
                } else {
                    items(openableApps, key = { "app_${it.packageName}" }) { app ->
                        // Build menu: all apps can have settings opened and be hidden, only user apps can be uninstalled
                        val menuItems = buildList {
                            add(LongPressMenuItem("App Info") {
                                launchAppSettings(context, app.packageName)
                            })
                            add(LongPressMenuItem("Hide App") {
                                HiddenAppsManager.hideApp(context, app.packageName)
                                hiddenPackages = HiddenAppsManager.getHiddenApps(context)
                            })
                            if (!app.isSystemApp) {
                                add(LongPressMenuItem("Uninstall App") {
                                    launchUninstall(context, app.packageName)
                                })
                            }
                        }

                        AppGridItem(
                            app = app,
                            onTap = { launchApp(context, app.packageName) },
                            longPressMenuItems = menuItems
                        )
                    }
                }

                item(span = { GridItemSpan(7) }) { Spacer(Modifier.height(8.dp)) }

                // ── Section 2 : Hidden Apps (user apps only) ────────────────
                item(span = { GridItemSpan(7) }) {
                    AppsSectionHeader(
                        title = "Hidden Apps", count = hiddenApps.size,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (hiddenApps.isEmpty()) {
                    item(span = { GridItemSpan(7) }) {
                        EmptySectionHint(
                            if (searchQuery.isBlank()) "No hidden apps — long-press a user app to hide it"
                            else "No hidden apps match \"$searchQuery\""
                        )
                    }
                } else {
                    items(hiddenApps, key = { "hidden_${it.packageName}" }) { app ->
                        AppGridItem(
                            app = app,
                            dimmed = true,
                            onTap = { launchApp(context, app.packageName) },
                            longPressMenuItems = listOf(
                                LongPressMenuItem("App Info") {
                                    launchAppSettings(context, app.packageName)
                                },
                                LongPressMenuItem("Unhide App") {
                                    HiddenAppsManager.unhideApp(context, app.packageName)
                                    hiddenPackages = HiddenAppsManager.getHiddenApps(context)
                                }
                            )
                        )
                    }
                }

                item(span = { GridItemSpan(7) }) { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sub-composables
// ---------------------------------------------------------------------------

@Composable
private fun AppsSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Search, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                decorationBox = { inner ->
                    if (query.isEmpty()) {
                        Text(
                            "Search apps…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    inner()
                }
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.Close, contentDescription = "Clear",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun AppsSectionHeader(
    title: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp, 18.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Text(
            text = "$title  ($count)",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(start = 8.dp)
        )
        Spacer(Modifier.weight(1f))
        HorizontalDivider(
            modifier = Modifier.weight(2f),
            color = color.copy(alpha = 0.3f)
        )
    }
}

@Composable
private fun EmptySectionHint(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp)
    )
}

data class LongPressMenuItem(val label: String, val action: () -> Unit)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppGridItem(
    app: InstalledAppInfo,
    onTap: () -> Unit,
    longPressMenuItems: List<LongPressMenuItem>,
    dimmed: Boolean = false
) {
    var showMenu by remember { mutableStateOf(false) }

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onTap,
                onLongClick = { if (longPressMenuItems.isNotEmpty()) showMenu = true }
            )
            .padding(vertical = 6.dp, horizontal = 2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            // App icon
            AppIconImage(
                packageName = app.packageName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (dimmed) Modifier.background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            RoundedCornerShape(10.dp)
                        ) else Modifier
                    )
            )

            Spacer(Modifier.height(3.dp))

            // App name — two lines
            Text(
                text = app.label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (dimmed)
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Context menu on long press
        if (longPressMenuItems.isNotEmpty()) {
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                longPressMenuItems.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(item.label) },
                        onClick = { showMenu = false; item.action() }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Icon loading
// ---------------------------------------------------------------------------

@Composable
private fun AppIconImage(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                drawableToBitmap(context.packageManager.getApplicationIcon(packageName))
                    .asImageBitmap()
            }.getOrNull()
        }
    }
    if (iconBitmap != null) {
        Image(bitmap = iconBitmap!!, contentDescription = null, modifier = modifier)
    } else {
        Icon(
            Icons.Filled.Android, contentDescription = null, modifier = modifier,
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) return drawable.bitmap
    val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
    val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, w, h)
    drawable.draw(canvas)
    return bmp
}

// ---------------------------------------------------------------------------
// App loading & launching
// ---------------------------------------------------------------------------

private fun loadInstalledApps(context: Context): List<InstalledAppInfo> {
    val pm = context.packageManager
    @Suppress("DEPRECATION")
    return pm.getInstalledApplications(PackageManager.GET_META_DATA)
        .mapNotNull { info ->
            if (info.packageName == context.packageName) return@mapNotNull null
            // Only include apps that can actually be launched (openable)
            if (pm.getLaunchIntentForPackage(info.packageName) == null) return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(info).toString() }
                .getOrElse { info.packageName }
            InstalledAppInfo(
                packageName = info.packageName,
                label = label,
                isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            )
        }
}

private fun launchApp(context: Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** Opens the system app settings page for any app. */
private fun launchAppSettings(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("ApplicationsScreen", "Failed to open app settings: ${e.message}", e)
        Toast.makeText(context, "Cannot open app settings", Toast.LENGTH_SHORT).show()
    }
}

/** Opens the system uninstall dialog for the given user-installed app. */
private fun launchUninstall(context: Context, packageName: String) {
    Log.d("ApplicationsScreen", "Attempting to uninstall: $packageName")
    
    // Primary: ACTION_UNINSTALL_PACKAGE — the dedicated per-app uninstall intent
    val primaryIntent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // Fallback 1: ACTION_DELETE — general delete with package URI
    val deleteIntent = Intent(Intent.ACTION_DELETE).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    // Fallback 2: open App Info in system Settings so user can uninstall manually
    val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    val tried = listOf(primaryIntent, deleteIntent, settingsIntent)
    for ((index, intent) in tried.withIndex()) {
        try {
            Log.d("ApplicationsScreen", "Trying intent ${index + 1}: ${intent.action}")
            context.startActivity(intent)
            Log.d("ApplicationsScreen", "Successfully launched intent ${index + 1}")
            return   // launched successfully
        } catch (e: ActivityNotFoundException) {
            Log.w("ApplicationsScreen", "ActivityNotFoundException for intent ${index + 1}: ${e.message}")
        } catch (e: SecurityException) {
            Log.w("ApplicationsScreen", "SecurityException for intent ${index + 1}: ${e.message}")
        } catch (e: Exception) {
            Log.e("ApplicationsScreen", "Exception for intent ${index + 1}: ${e.message}", e)
        }
    }
    // Nothing worked
    Log.e("ApplicationsScreen", "All uninstall intents failed for package: $packageName")
    Toast.makeText(context, "Cannot open uninstall dialog for this app", Toast.LENGTH_SHORT).show()
}

