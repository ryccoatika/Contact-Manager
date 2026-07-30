package com.ryccoatika.contactmanager.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAppPrefs(
    private var asked: Boolean = false,
    hiddenAccountKeys: Set<String> = emptySet(),
) : AppPrefs {
    private val hidden = MutableStateFlow(hiddenAccountKeys)

    override suspend fun phonePermissionAsked(): Boolean = asked
    override suspend fun setPhonePermissionAsked() { asked = true }

    override fun observeHiddenAccountKeys(): Flow<Set<String>> = hidden
    override suspend fun setAccountHidden(key: String, hidden: Boolean) {
        this.hidden.value = if (hidden) this.hidden.value + key else this.hidden.value - key
    }
}
