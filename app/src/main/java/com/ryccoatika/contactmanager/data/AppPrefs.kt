package com.ryccoatika.contactmanager.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ryccoatika.contactmanager.domain.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Small app-wide flags. READ_PHONE_STATE is requested lazily the first time a
 * SIM pseudo-account is on screen; the asked flag makes that prompt one-time.
 */
interface AppPrefs {
    suspend fun phonePermissionAsked(): Boolean
    suspend fun setPhonePermissionAsked()

    /** Account keys ("type/name") the user hid from the Home selector. */
    fun observeHiddenAccountKeys(): Flow<Set<String>>
    suspend fun setAccountHidden(key: String, hidden: Boolean)

    /** False until the user finishes the first-launch onboarding. */
    fun observeOnboardingSeen(): Flow<Boolean>
    suspend fun setOnboardingSeen()

    /** The user's theme choice; SYSTEM (follow device) until they pick otherwise. */
    fun observeThemeMode(): Flow<ThemeMode>
    suspend fun setThemeMode(mode: ThemeMode)
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

    override fun observeHiddenAccountKeys(): Flow<Set<String>> =
        context.appPrefsDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[HIDDEN_ACCOUNT_KEYS] ?: emptySet() }

    override suspend fun setAccountHidden(key: String, hidden: Boolean) {
        try {
            context.appPrefsDataStore.edit { prefs ->
                val current = prefs[HIDDEN_ACCOUNT_KEYS] ?: emptySet()
                prefs[HIDDEN_ACCOUNT_KEYS] = if (hidden) current + key else current - key
            }
        } catch (_: IOException) {
            // Best-effort: a hidden account simply stays visible.
        }
    }

    override fun observeOnboardingSeen(): Flow<Boolean> =
        context.appPrefsDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it[ONBOARDING_SEEN] ?: false }

    override suspend fun setOnboardingSeen() {
        try {
            context.appPrefsDataStore.edit { prefs -> prefs[ONBOARDING_SEEN] = true }
        } catch (_: IOException) {
            // Best-effort: onboarding may show once more.
        }
    }

    override fun observeThemeMode(): Flow<ThemeMode> =
        context.appPrefsDataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { prefs ->
                // Unknown/absent value falls back to following the system.
                prefs[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                    ?: ThemeMode.SYSTEM
            }

    override suspend fun setThemeMode(mode: ThemeMode) {
        try {
            context.appPrefsDataStore.edit { prefs -> prefs[THEME_MODE] = mode.name }
        } catch (_: IOException) {
            // Best-effort: the theme simply stays as it was.
        }
    }

    private companion object {
        val PHONE_PERMISSION_ASKED = booleanPreferencesKey("phone_permission_asked")
        val HIDDEN_ACCOUNT_KEYS = stringSetPreferencesKey("hidden_account_keys")
        val ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
