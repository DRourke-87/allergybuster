package com.drourke.allergybuster.ui.settings

import android.content.Context
import android.content.Intent
import android.location.Geocoder
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drourke.allergybuster.data.local.datastore.AppSettings
import com.drourke.allergybuster.data.local.datastore.AppSettingsDataStore
import com.drourke.allergybuster.data.location.LocationProvider
import com.drourke.allergybuster.notification.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: AppSettingsDataStore,
    private val locationProvider: LocationProvider,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    val settings: StateFlow<AppSettings> = dataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _shareIntent = MutableSharedFlow<Intent>()
    val shareIntent: SharedFlow<Intent> = _shareIntent

    fun setNotificationTime(hour: Int, minute: Int) {
        viewModelScope.launch { dataStore.setNotificationTime(hour, minute) }
    }

    fun refreshLocation() {
        viewModelScope.launch {
            locationProvider.getLocation()?.let { loc ->
                dataStore.setLocation(loc.lat, loc.lon, loc.name)
            }
        }
    }

    fun setPersistentNotifEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.setPersistentNotifEnabled(enabled)
            if (!enabled) notificationHelper.cancelPersistentNotification()
        }
    }

    fun exportLocationDebug() {
        viewModelScope.launch(Dispatchers.IO) {
            val stored = dataStore.settingsFlow.first()
            val lat = stored.locationLat
            val lon = stored.locationLon

            val addr = try {
                Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            } catch (_: Exception) { null }

            val text = buildString {
                appendLine("AllergyBuster — Location Debug")
                appendLine("Generated: ${Instant.now()}")
                appendLine()
                appendLine("Stored coords : $lat, $lon")
                appendLine("Stored name   : ${stored.locationName}")
                appendLine()
                if (addr != null) {
                    appendLine("Geocoder Address fields")
                    appendLine("  featureName     = ${addr.featureName}")
                    appendLine("  premises        = ${addr.premises}")
                    appendLine("  subThoroughfare = ${addr.subThoroughfare}")
                    appendLine("  thoroughfare    = ${addr.thoroughfare}")
                    appendLine("  subLocality     = ${addr.subLocality}")
                    appendLine("  locality        = ${addr.locality}")
                    appendLine("  subAdminArea    = ${addr.subAdminArea}")
                    appendLine("  adminArea       = ${addr.adminArea}")
                    appendLine("  postalCode      = ${addr.postalCode}")
                    appendLine("  countryName     = ${addr.countryName}")
                    appendLine("  countryCode     = ${addr.countryCode}")
                    appendLine()
                    appendLine("Full address lines:")
                    for (i in 0..addr.maxAddressLineIndex) {
                        appendLine("  [$i] ${addr.getAddressLine(i)}")
                    }
                } else {
                    appendLine("No geocoder result returned for these coordinates.")
                }
            }

            val file = File(context.cacheDir, "location_debug.txt")
            file.writeText(text)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "AllergyBuster Location Debug")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            _shareIntent.emit(Intent.createChooser(intent, "Export location debug"))
        }
    }
}
