package com.ryccoatika.contactmanager.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ryccoatika.contactmanager.data.AppPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val appPrefs: AppPrefs,
) : ViewModel() {

    /** null while loading, true = show onboarding, false = go straight to the app. */
    val showOnboarding: StateFlow<Boolean?> = appPrefs.observeOnboardingSeen()
        .map { seen -> !seen }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun complete() {
        viewModelScope.launch { appPrefs.setOnboardingSeen() }
    }
}
