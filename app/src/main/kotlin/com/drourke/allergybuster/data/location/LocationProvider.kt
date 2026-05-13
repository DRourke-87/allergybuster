package com.drourke.allergybuster.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceLocation(val lat: Double, val lon: Double, val name: String)

@Singleton
class LocationProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun getLocation(): DeviceLocation? = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return@withContext null

        val lm = context.getSystemService(LocationManager::class.java)
            ?: return@withContext null

        val loc: Location? = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)

        loc?.let { DeviceLocation(it.latitude, it.longitude, geocode(it.latitude, it.longitude)) }
    }

    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0] / 1000f
    }

    private fun geocode(lat: Double, lon: Double): String = try {
        Geocoder(context, Locale.getDefault())
            .getFromLocation(lat, lon, 1)
            ?.firstOrNull()
            ?.let { addr ->
                addr.locality
                    ?: addr.featureName?.takeIf { it != addr.adminArea && it != addr.countryName }
                    ?: addr.subAdminArea
                    ?: addr.adminArea
            }
            ?: "%.2f°, %.2f°".format(lat, lon)
    } catch (_: Exception) {
        "%.2f°, %.2f°".format(lat, lon)
    }
}
