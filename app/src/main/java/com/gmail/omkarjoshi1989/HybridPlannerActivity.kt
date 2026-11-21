package com.gmail.omkarjoshi1989

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gmail.omkarjoshi1989.ui.screens.HybridPlannerScreen
import com.gmail.omkarjoshi1989.ui.theme.FilesTheme

/** Hybrid (home/office) planner backed by Supabase. Launched from drawer and app-icon long-press shortcut. */
class HybridPlannerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FilesTheme {
                HybridPlannerScreen(onBack = { finish() })
            }
        }
    }
}

