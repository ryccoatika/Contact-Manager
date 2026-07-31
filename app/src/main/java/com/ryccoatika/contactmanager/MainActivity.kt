package com.ryccoatika.contactmanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.ryccoatika.contactmanager.data.analytics.Analytics
import com.ryccoatika.contactmanager.ui.AppNav
import com.ryccoatika.contactmanager.ui.analytics.LocalAnalytics
import com.ryccoatika.contactmanager.ui.theme.ContactManagerTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var analytics: Analytics

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ContactManagerTheme {
                CompositionLocalProvider(LocalAnalytics provides analytics) {
                    AppNav()
                }
            }
        }
    }
}
