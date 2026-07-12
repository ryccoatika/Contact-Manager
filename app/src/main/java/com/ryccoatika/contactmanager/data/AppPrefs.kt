package com.ryccoatika.contactmanager.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

/**
 * Small app-wide flags. READ_PHONE_STATE is requested lazily the first time a
 * SIM pseudo-account is on screen; the asked flag makes that prompt one-time.
 */
interface AppPrefs {
    suspend fun phonePermissionAsked(): Boolean
    suspend fun setPhonePermissionAsked()
}

private val Context.appPrefsDataStore by preferencesDataStore(name = "app_prefs")

@Singleton
class DataStoreAppPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppPrefs {

    override suspend fun phonePermissionAsked(): Boolean =
        context.appPrefsDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .first()[PHONE_PERMISSION_ASKED] ?: false

    override suspend fun setPhonePermissionAsked() {
        try {
            context.appPrefsDataStore.edit { prefs -> prefs[PHONE_PERMISSION_ASKED] = true }
        } catch (_: IOException) {
            // Best-effort: losing the flag only re-shows the one-time prompt.
        }
    }

    private companion object {
        val PHONE_PERMISSION_ASKED = booleanPreferencesKey("phone_permission_asked")
    }
}
