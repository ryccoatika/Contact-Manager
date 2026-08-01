package com.ryccoatika.contactmanager.ui.adaptive

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowWidthSizeClass
import com.ryccoatika.contactmanager.ui.MainNavGraph

/** Compact width keeps the phone NavHost; medium/expanded use the tablet shell. */
@Composable
fun AdaptiveApp() {
    val widthClass = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass
    if (widthClass == WindowWidthSizeClass.COMPACT) {
        MainNavGraph()
    } else {
        TabletShell()
    }
}
