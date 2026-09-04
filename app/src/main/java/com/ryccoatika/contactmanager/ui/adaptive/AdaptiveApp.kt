package com.ryccoatika.contactmanager.ui.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass
import com.ryccoatika.contactmanager.ui.MainNavGraph

/** Compact width keeps the phone NavHost; medium/expanded use the tablet shell. */
@Composable
fun AdaptiveApp() {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    if (windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        TabletShell()
    } else {
        MainNavGraph()
    }
}
