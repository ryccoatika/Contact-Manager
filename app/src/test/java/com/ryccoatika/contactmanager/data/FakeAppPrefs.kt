package com.ryccoatika.contactmanager.data

class FakeAppPrefs(
    private var asked: Boolean = false,
) : AppPrefs {
    override suspend fun phonePermissionAsked(): Boolean = asked
    override suspend fun setPhonePermissionAsked() { asked = true }
}
