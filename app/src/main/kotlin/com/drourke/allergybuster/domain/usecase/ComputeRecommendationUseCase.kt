package com.drourke.allergybuster.domain.usecase

import com.drourke.allergybuster.data.repository.FeedbackRepository
import com.drourke.allergybuster.domain.engine.RecommendationEngine
import com.drourke.allergybuster.domain.model.DailyPollen
import com.drourke.allergybuster.domain.model.Recommendation
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ComputeRecommendationUseCase @Inject constructor(
    private val feedbackRepository: FeedbackRepository
) {
    suspend operator fun invoke(pollen: DailyPollen, isStale: Boolean = false): Recommendation {
        val weights      = feedbackRepository.getWeights()
        val score        = RecommendationEngine.computeScore(pollen, weights)
        val level        = RecommendationEngine.scoreToLevel(score)
        val contributors = RecommendationEngine.computeContributions(pollen, weights)
        return Recommendation(
            date            = LocalDate.now().toString(),
            level           = level,
            score           = score,
            advice          = RecommendationEngine.levelToAdvice(level),
            topContributors = contributors,
            computedAt      = System.currentTimeMillis(),
            isStale         = isStale
        )
    }
}
