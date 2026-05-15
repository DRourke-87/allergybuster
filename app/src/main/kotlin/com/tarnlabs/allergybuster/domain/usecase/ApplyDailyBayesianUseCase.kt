package com.tarnlabs.allergybuster.domain.usecase

import com.tarnlabs.allergybuster.data.repository.FeedbackRepository
import com.tarnlabs.allergybuster.data.repository.PollenRepository
import com.tarnlabs.allergybuster.data.repository.RecommendationRepository
import com.tarnlabs.allergybuster.domain.engine.BayesianUpdater
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies Bayesian weight updates for any previous day's feedback that hasn't been processed yet.
 * Called by PollenFetchWorker each morning so the update reflects what the user left their feeling
 * set to at the end of the day, not just their first tap.
 */
@Singleton
class ApplyDailyBayesianUseCase @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val pollenRepository: PollenRepository,
    private val recommendationRepository: RecommendationRepository
) {
    suspend operator fun invoke() {
        val today = LocalDate.now().toString()
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
