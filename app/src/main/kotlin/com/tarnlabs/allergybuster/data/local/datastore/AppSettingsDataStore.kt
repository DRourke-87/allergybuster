package com.tarnlabs.allergybuster.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tarnlabs.allergybuster.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val notificationHour: Int = 6,
    val notificationMinute: Int = 0,
    val onboardingDone: Boolean = false,
    val locationLat: Double = 54.66,
    val locationLon: Double = -3.36,
    val locationName: String = "",
    val persistentNotifEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

@Singleton
class AppSettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val NOTIFICATION_HOUR        = intPreferencesKey("notification_hour")
        val NOTIFICATION_MINUTE      = intPreferencesKey("notification_minute")
        val ONBOARDING_DONE          = booleanPreferencesKey("onboarding_done")
        val LOCATION_LAT             = doublePreferencesKey("location_lat")
        val LOCATION_LON             = doublePreferencesKey("location_lon")
        val LOCATION_NAME            = stringPreferencesKey("location_name")
        val LEARNING_STARTED_AT      = longPreferencesKey("learning_started_at")
        val PERSISTENT_NOTIF_ENABLED = booleanPreferencesKey("persistent_notif_enabled")
        val THEME_MODE               = stringPreferencesKey("theme_mode")
        val ROOM_MIGRATION_DONE      = booleanPreferencesKey("room_migration_done")
        val LAST_APP_VERSION_CODE    = intPreferencesKey("last_app_version_code")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            notificationHour        = prefs[Keys.NOTIFICATION_HOUR]        ?: 6,
            notificationMinute      = prefs[Keys.NOTIFICATION_MINUTE]      ?: 0,
            onboardingDone          = prefs[Keys.ONBOARDING_DONE]          ?: false,
            locationLat             = prefs[Keys.LOCATION_LAT]             ?: 54.66,
            locationLon             = prefs[Keys.LOCATION_LON]             ?: -3.36,
            locationName            = prefs[Keys.LOCATION_NAME]            ?: "",
            persistentNotifEnabled  = prefs[Keys.PERSISTENT_NOTIF_ENABLED] ?: true,
            themeMode               = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM
        )
    }

    val learningStartedAtFlow: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LEARNING_STARTED_AT] ?: 0L
    }

    // Guards against both null (never set) and 0L (written by an older build).
    suspend fun ensureLearningStarted() {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.LEARNING_STARTED_AT]
            if (current == null || current == 0L) {
                prefs[Keys.LEARNING_STARTED_AT] = System.currentTimeMillis()
            }
        }
    }

    suspend fun setNotificationTime(hour: Int, minute: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.NOTIFICATION_HOUR]   = hour
            prefs[Keys.NOTIFICATION_MINUTE] = minute
        }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ONBOARDING_DONE] = true
        }
    }

    suspend fun setLocation(lat: Double, lon: Double, name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LOCATION_LAT]  = lat
            prefs[Keys.LOCATION_LON]  = lon
            prefs[Keys.LOCATION_NAME] = name
        }
    }

    suspend fun setPersistentNotifEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PERSISTENT_NOTIF_ENABLED] = enabled
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    val roomMigrationDoneFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ROOM_MIGRATION_DONE] ?: false
    }

    suspend fun markRoomMigrationDone() {
        context.dataStore.edit { prefs ->
            prefs[Keys.ROOM_MIGRATION_DONE] = true
        }
    }

    suspend fun clearRoomMigrationFlag() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.ROOM_MIGRATION_DONE)
        }
    }

    suspend fun getLastAppVersionCode(): Int? =
        context.dataStore.data.first()[Keys.LAST_APP_VERSION_CODE]

    suspend fun setLastAppVersionCode(code: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_APP_VERSION_CODE] = code
        }
    }
}
