package com.drourke.allergybuster.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drourke.allergybuster.data.local.datastore.AppSettings
import com.drourke.allergybuster.data.local.datastore.AppSettingsDataStore
import com.drourke.allergybuster.data.location.LocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dataStore: AppSettingsDataStore,
    private val locationProvider: LocationProvider
) : ViewModel() {

    val settings: StateFlow<AppSettings> = dataStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

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
}
