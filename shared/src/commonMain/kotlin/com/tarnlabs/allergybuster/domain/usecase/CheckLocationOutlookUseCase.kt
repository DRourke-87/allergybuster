package com.tarnlabs.allergybuster.domain.usecase

import com.tarnlabs.allergybuster.data.remote.OpenMeteoApiClient
import com.tarnlabs.allergybuster.data.remote.dto.toDailyPollen
import com.tarnlabs.allergybuster.data.repository.FeedbackRepository
import com.tarnlabs.allergybuster.domain.engine.RecommendationEngine
import com.tarnlabs.allergybuster.domain.model.DailyOutlook

/**
 * Fetches the forecast for an arbitrary location and scores it live against the
 * user's learned weights. Nothing is persisted — this is a read-only lookup for
 * trip planning. Days are bucketed in the searched location's own timezone
 * ("auto"), so "tomorrow" means tomorrow there. Throws on network failure.
 */
class CheckLocationOutlookUseCase(
    private val api: OpenMeteoApiClient,
    private val feedbackRepository: FeedbackRepository
) {
    suspend operator fun invoke(lat: Double, lon: Double): List<DailyOutlook> {
        val response = api.getAirQuality(lat, lon, OpenMeteoApiClient.POLLEN_HOURLY_FIELDS, 4, "auto")
        val weights = feedbackRepository.getWeights()
        return response.toDailyPollen()
            .sortedBy { it.date }
            .map { RecommendationEngine.computeOutlook(it, weights) }
    }
}
