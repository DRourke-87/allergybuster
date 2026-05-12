package com.drourke.allergybuster.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.drourke.allergybuster.data.repository.PollenRepository
import com.drourke.allergybuster.data.repository.RecommendationRepository
import com.drourke.allergybuster.domain.usecase.ComputeRecommendationUseCase
import com.drourke.allergybuster.notification.NotificationHelper
import com.drourke.allergybuster.widget.AllergyWidgetReceiver
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

@HiltWorker
class PollenFetchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pollenRepository: PollenRepository,
    private val recommendationRepository: RecommendationRepository,
    private val computeRecommendation: ComputeRecommendationUseCase,
    private val notificationHelper: NotificationHelper
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val today = LocalDate.now().toString()

            // 1. Try to fetch fresh pollen data
            val (pollen, isStale) = pollenRepository.fetchAndStore()?.let { it to false }
                ?: pollenRepository.getCachedForDate(today)?.let { it to false }
                ?: pollenRepository.getMostRecent()?.let { it to true }
                ?: return if (runAttemptCount < 3) Result.retry() else Result.failure()

            // 2. Compute and persist recommendation
            val recommendation = computeRecommendation(pollen, isStale)
            recommendationRepository.save(recommendation)

            // 3. Prune old forecasts
            pollenRepository.pruneOldForecasts()

            // 4. Update Glance widget
            AllergyWidgetReceiver.updateWidget(applicationContext)

            // 5. Post morning alert (dismissable, with feedback actions)
            notificationHelper.postDailyNotification(recommendation)

            // 6. Refresh the persistent status notification in the shade
            notificationHelper.postPersistentNotification(recommendation)

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
