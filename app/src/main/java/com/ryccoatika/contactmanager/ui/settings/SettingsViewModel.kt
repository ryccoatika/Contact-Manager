package com.ryccoatika.contactmanager.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.contactmanager.data.AppPrefs
import com.ryccoatika.contactmanager.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val appPrefs: AppPrefs,
    ) : ViewModel() {
        val themeMode: StateFlow<ThemeMode> = appPrefs
            .observeThemeMode()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.SYSTEM)

        fun setThemeMode(mode: ThemeMode) {
            viewModelScope.launch { appPrefs.setThemeMode(mode) }
        }
    }
