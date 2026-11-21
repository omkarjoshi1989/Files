package com.gmail.omkarjoshi1989.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gmail.omkarjoshi1989.model.HybridEntry
import java.time.format.DateTimeFormatter
import com.gmail.omkarjoshi1989.model.HybridDay
import com.gmail.omkarjoshi1989.model.PlannedHybridMode
import com.gmail.omkarjoshi1989.util.HybridRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HybridPlannerScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val months = remember { HybridRepository.threeMonths() }

    var entries by remember { mutableStateOf<List<HybridEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableStateOf(1) } // current month
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        loading = true
        val error = try {
            entries = HybridRepository.loadThreeMonths()
            null
        } catch (e: Exception) {
            e.message ?: "Failed to load Hybrid planner"
        }
        loading = false
        error?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Hybrid Planner") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { reloadKey++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = selectedTab) {
                months.forEachIndexed { i, ym ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(monthLabel(ym)) }
                    )
                }
            }
            if (loading && entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val ym = months[selectedTab]
                val monthEntries = entries.filter { YearMonth.from(it.date) == ym }
                val today = LocalDate.now()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(monthEntries, key = { it.date.toString() }) { entry ->
                        HybridRow(
                            entry = entry,
                            isToday = entry.date == today,
                            onModeSelected = { mode ->
                                if (mode != entry.mode) scope.launch {
                                    try {
                                        val updated = HybridRepository.updateMode(entry, mode, monthEntries)
                                        entries = entries.filter { YearMonth.from(it.date) != ym } + updated
                                        entries = entries.sortedBy { it.date }
                                    } catch (e: Exception) {
                                        snackbar.showSnackbar(e.message ?: "Update failed")
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HybridRow(entry: HybridEntry, isToday: Boolean, onModeSelected: (PlannedHybridMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.width(110.dp)) {
                Text(
                    entry.date.format(DATE_FORMAT),
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(entry.day.dbValue, style = MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.weight(1f)) {
                Text(
                    text = entry.mode.dbValue,
                    modifier = Modifier.clickable { expanded = true },
                    style = MaterialTheme.typography.titleMedium
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    allowedModes(entry.day).forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.dbValue) },
                            onClick = { expanded = false; onModeSelected(mode) }
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                // e.g. H1 / O2 / O3 : H_O_W code + running count of the selected planned mode in the month
                val code = entry.how?.dbValue
                Text(
                    if (code != null) code + (entry.serial ?: "") else "",
                    style = MaterialTheme.typography.titleLarge
                )
                entry.status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

/** Saturday/Sunday: weekend or comp-off only. Other days: everything except weekend. */
private fun allowedModes(day: HybridDay): List<PlannedHybridMode> =
    if (day == HybridDay.Saturday || day == HybridDay.Sunday)
        listOf(PlannedHybridMode.WEEKEND, PlannedHybridMode.COMP_OFF)
    else PlannedHybridMode.entries.filter { it != PlannedHybridMode.WEEKEND }

private fun monthLabel(ym: YearMonth): String =
    ym.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + ym.year

