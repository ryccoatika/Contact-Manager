package com.ryccoatika.contactmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ryccoatika.contactmanager.data.AppPrefs
import com.ryccoatika.contactmanager.data.analytics.Analytics
import com.ryccoatika.contactmanager.domain.model.ThemeMode
import com.ryccoatika.contactmanager.ui.AppNav
import com.ryccoatika.contactmanager.ui.analytics.LocalAnalytics
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme
import com.ryccoatika.contactmanager.ui.update.InAppUpdate
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var analytics: Analytics
    @Inject lateinit var appPrefs: AppPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by appPrefs.observeThemeMode()
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            ContactManagerTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalAnalytics provides analytics) {
                    // App-level snackbar host, kept above the screen content so the
                    // flexible in-app-update "restart" prompt can show over any screen.
                    val updateSnackbarHost = remember { SnackbarHostState() }
                    Box(Modifier.fillMaxSize()) {
                        AppNav()
                        InAppUpdate(updateSnackbarHost)
                        SnackbarHost(
                            updateSnackbarHost,
                            Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding(),
                        )
                    }
                }
            }
        }
    }
}
