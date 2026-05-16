package com.tarnlabs.allergybuster.domain.usecase

import com.tarnlabs.allergybuster.data.repository.FeedbackRepository
import com.tarnlabs.allergybuster.data.repository.PollenRepository
import com.tarnlabs.allergybuster.data.repository.RecommendationRepository
import com.tarnlabs.allergybuster.domain.engine.BayesianUpdater
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

class ApplyDailyBayesianUseCase(
    private val feedbackRepository: FeedbackRepository,
    private val pollenRepository: PollenRepository,
    private val recommendationRepository: RecommendationRepository
) {
    suspend operator fun invoke() {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        val pending = feedbackRepository.getPendingBayesianUpdates(today)
        for (feedback in pending) {
            val pollen = pollenRepository.getCachedForDate(feedback.date) ?: continue
            val rec    = recommendationRepository.getForDate(feedback.date) ?: continue
            val currentWeights = feedbackRepository.getWeights()
            val updatedWeights = BayesianUpdater.updateWeights(
                current        = currentWeights,
                pollen         = pollen,
                actualSeverity = feedback.severity,
                predictedLevel = rec.level
            )
            feedbackRepository.saveWeights(updatedWeights)
            feedbackRepository.markBayesianApplied(feedback.date)
        }
    }
}
