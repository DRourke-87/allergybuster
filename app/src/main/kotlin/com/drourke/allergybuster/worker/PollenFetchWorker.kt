package com.drourke.allergybuster.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.drourke.allergybuster.data.local.datastore.AppSettingsDataStore
import com.drourke.allergybuster.data.location.LocationProvider
import com.drourke.allergybuster.data.repository.PollenRepository
import com.drourke.allergybuster.data.repository.RecommendationRepository
import com.drourke.allergybuster.domain.usecase.ComputeRecommendationUseCase
import com.drourke.allergybuster.notification.NotificationHelper
import com.drourke.allergybuster.widget.AllergyWidgetReceiver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

@HiltWorker
class PollenFetchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pollenRepository: PollenRepository,
    private val recommendationRepository: RecommendationRepository,
    private val computeRecommendation: ComputeRecommendationUseCase,
    private val notificationHelper: NotificationHelper,
    private val locationProvider: LocationProvider,
    private val settingsDataStore: AppSettingsDataStore
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val today = LocalDate.now().toString()

            // 1. Resolve current location
            val settings = settingsDataStore.settingsFlow.first()
            val deviceLoc = locationProvider.getLocation()
            val (lat, lon, locationName) = if (deviceLoc != null) {
                val distKm = locationProvider.distanceKm(
                    deviceLoc.lat, deviceLoc.lon,
                    settings.locationLat, settings.locationLon
                )
                if (distKm > 10f) {
                    // Moved significantly — update stored location and prompt re-rating
                    settingsDataStore.setLocation(deviceLoc.lat, deviceLoc.lon, deviceLoc.name)
                    notificationHelper.postLocationChangedNotification(deviceLoc.name, today)
                    Triple(deviceLoc.lat, deviceLoc.lon, deviceLoc.name)
                } else {
                    Triple(settings.locationLat, settings.locationLon, settings.locationName)
                }
            } else {
                Triple(settings.locationLat, settings.locationLon, settings.locationName)
            }

            // 2. Try to fetch fresh pollen data for resolved location
            val (pollen, isStale) = pollenRepository.fetchAndStore(lat, lon)?.let { it to false }
                ?: pollenRepository.getCachedForDate(today)?.let { it to false }
                ?: pollenRepository.getMostRecent()?.let { it to true }
                ?: return if (runAttemptCount < 3) Result.retry() else Result.failure()

            // 3. Compute and persist recommendation
            val recommendation = computeRecommendation(pollen, isStale, locationName)
            recommendationRepository.save(recommendation)

            // 4. Prune old forecasts
            pollenRepository.pruneOldForecasts()

            // 5. Update Glance widget
            AllergyWidgetReceiver.updateWidget(applicationContext)

            // 6. Post morning alert (dismissable, with feedback actions)
            notificationHelper.postDailyNotification(recommendation)

            // 7. Refresh the persistent status notification in the shade (if enabled)
            if (settings.persistentNotifEnabled) {
                notificationHelper.postPersistentNotification(recommendation)
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
