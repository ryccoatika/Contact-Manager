package com.ryccoatika.contactmanager.data

import com.ryccoatika.contactmanager.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppPrefs(
    private var asked: Boolean = false,
    hiddenAccountKeys: Set<String> = emptySet(),
    onboardingSeen: Boolean = true,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
) : AppPrefs {
    private val hidden = MutableStateFlow(hiddenAccountKeys)
    private val onboarding = MutableStateFlow(onboardingSeen)
    private val theme = MutableStateFlow(themeMode)

    override suspend fun phonePermissionAsked(): Boolean = asked

    override suspend fun setPhonePermissionAsked() {
        asked = true
    }

    override fun observeHiddenAccountKeys(): Flow<Set<String>> = hidden

    override suspend fun setAccountHidden(key: String, hidden: Boolean) {
        this.hidden.value = if (hidden) this.hidden.value + key else this.hidden.value - key
    }

    override fun observeOnboardingSeen(): Flow<Boolean> = onboarding

    override suspend fun setOnboardingSeen() {
        onboarding.value = true
    }

    override fun observeThemeMode(): Flow<ThemeMode> = theme

    override suspend fun setThemeMode(mode: ThemeMode) {
        theme.value = mode
    }
}
