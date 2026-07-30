package com.ryccoatika.contactmanager.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppPrefs(
    private var asked: Boolean = false,
    hiddenAccountKeys: Set<String> = emptySet(),
    onboardingSeen: Boolean = true,
) : AppPrefs {
    private val hidden = MutableStateFlow(hiddenAccountKeys)
    private val onboarding = MutableStateFlow(onboardingSeen)

    override suspend fun phonePermissionAsked(): Boolean = asked
    override suspend fun setPhonePermissionAsked() { asked = true }

    override fun observeHiddenAccountKeys(): Flow<Set<String>> = hidden
    override suspend fun setAccountHidden(key: String, hidden: Boolean) {
        this.hidden.value = if (hidden) this.hidden.value + key else this.hidden.value - key
    }

    override fun observeOnboardingSeen(): Flow<Boolean> = onboarding
    override suspend fun setOnboardingSeen() { onboarding.value = true }
}
