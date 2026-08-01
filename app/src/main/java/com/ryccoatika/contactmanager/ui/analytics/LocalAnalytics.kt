package com.ryccoatika.contactmanager.ui.analytics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import com.ryccoatika.contactmanager.data.analytics.Analytics
import com.ryccoatika.contactmanager.data.analytics.NoOpAnalytics

/** Analytics for composables; provided at the app root, defaults to no-op. */
val LocalAnalytics: ProvidableCompositionLocal<Analytics> = staticCompositionLocalOf { NoOpAnalytics }

/** Logs a single screen_view for [screenName] when this composable enters composition. */
@Composable
fun TrackScreenView(screenName: String) {
    val analytics = LocalAnalytics.current
    LaunchedEffect(screenName) { analytics.logScreenView(screenName) }
}
