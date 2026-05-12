package com.drourke.allergybuster.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
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
    val locationName: String = "Cockermouth"
)

@Singleton
class AppSettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val NOTIFICATION_HOUR   = intPreferencesKey("notification_hour")
        val NOTIFICATION_MINUTE = intPreferencesKey("notification_minute")
        val ONBOARDING_DONE     = booleanPreferencesKey("onboarding_done")
        val LOCATION_LAT        = doublePreferencesKey("location_lat")
        val LOCATION_LON        = doublePreferencesKey("location_lon")
        val LOCATION_NAME       = stringPreferencesKey("location_name")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            notificationHour   = prefs[Keys.NOTIFICATION_HOUR]   ?: 6,
            notificationMinute = prefs[Keys.NOTIFICATION_MINUTE] ?: 0,
            onboardingDone     = prefs[Keys.ONBOARDING_DONE]     ?: false,
            locationLat        = prefs[Keys.LOCATION_LAT]        ?: 54.66,
            locationLon        = prefs[Keys.LOCATION_LON]        ?: -3.36,
            locationName       = prefs[Keys.LOCATION_NAME]       ?: "Cockermouth"
        )
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
}
