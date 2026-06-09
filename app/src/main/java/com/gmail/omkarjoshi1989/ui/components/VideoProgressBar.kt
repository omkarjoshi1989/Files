package com.gmail.omkarjoshi1989.ui.components

import android.media.MediaMetadataRetriever
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shows a thin progress bar + percentage label for a video file that has a
 * partially-saved playback position in SharedPreferences("media_positions").
 *
 * Renders nothing when:
 *  - no position has been saved for this file
 *  - the saved position corresponds to 0 % or 100 % (completed / never started)
 *
 * Automatically re-reads the saved position whenever the screen resumes so the
 * bar updates immediately after the user returns from the video player.
 */
@Composable
fun VideoProgressBar(file: File) {
    val context = LocalContext.current

    // Increment on every ON_RESUME → triggers produceState to re-read SharedPreferences.
    var resumeTick by remember(file.absolutePath) { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // -1f = no saved progress; 0..1f = actual fraction
    val progressFraction by produceState(
        initialValue = -1f,
        key1 = file.absolutePath,
        key2 = file.lastModified(),
        key3 = resumeTick
    ) {
        value = withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences(
                    "media_positions", android.content.Context.MODE_PRIVATE
                )
                val pos = prefs.getLong(file.absolutePath, 0L)
                if (pos <= 0L) return@withContext -1f

                val retriever = MediaMetadataRetriever()
                val dur = try {
                    retriever.setDataSource(file.absolutePath)
                    retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_DURATION
                    )?.toLongOrNull() ?: 0L
                } catch (_: Exception) {
                    0L
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }

                if (dur <= 0L) -1f
                else (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
            } catch (_: Exception) {
                -1f
            }
        }
    }

    val percent = (progressFraction * 100).toInt()
    if (progressFraction > 0f && percent in 1..99) {
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = "$percent%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

