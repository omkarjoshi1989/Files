package com.gmail.omkarjoshi1989.viewmodel

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide singleton that holds the current clipboard state.
 *
 * Sharing a single [MutableStateFlow] across all [FileExplorerViewModel] instances
 * ensures that a file cut/copied in one screen (e.g. "Images" collection) is still
 * available for pasting after the user switches to another screen (e.g. "All Files").
 */
object ClipboardRepository {
    val clipboard: MutableStateFlow<ClipboardData?> = MutableStateFlow(null)
}

