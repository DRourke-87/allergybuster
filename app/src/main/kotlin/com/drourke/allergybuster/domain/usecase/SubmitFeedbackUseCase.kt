package com.drourke.allergybuster.domain.usecase

import com.drourke.allergybuster.data.repository.FeedbackRepository
import com.drourke.allergybuster.data.repository.PollenRepository
import com.drourke.allergybuster.data.repository.RecommendationRepository
import com.drourke.allergybuster.domain.engine.BayesianUpdater
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SubmitFeedbackUseCase @Inject constructor(
    private val feedbackRepository: FeedbackRepository,
    private val pollenRepository: PollenRepository,
    private val recommendationRepository: RecommendationRepository
) {
    suspend operator fun invoke(date: String, severity: Int) {
        feedbackRepository.saveFeedback(date, severity)

        val pollen = pollenRepository.getCachedForDate(date) ?: return
        val rec    = recommendationRepository.getForDate(date) ?: return

        val currentWeights = feedbackRepository.getWeights()
        val updatedWeights = BayesianUpdater.updateWeights(
            current        = currentWeights,
            pollen         = pollen,
            actualSeverity = severity,
            predictedLevel = rec.level
        )
        feedbackRepository.saveWeights(updatedWeights)
    }
}
