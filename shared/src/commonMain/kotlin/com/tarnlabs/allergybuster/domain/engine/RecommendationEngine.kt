package com.tarnlabs.allergybuster.domain.engine

import com.tarnlabs.allergybuster.domain.model.DailyPollen
import com.tarnlabs.allergybuster.domain.model.PollenType
import com.tarnlabs.allergybuster.domain.model.UserWeights
import com.tarnlabs.allergybuster.domain.model.getWeight

object RecommendationEngine {

    fun computeScore(pollen: DailyPollen, weights: UserWeights): Float {
        var maxScore = 0f
        for (type in PollenType.entries) {
            val norm = type.normalise(pollen.getRaw(type))
            val weight = weights.getWeight(type)
            maxScore = maxOf(maxScore, norm * weight)
        }
        return maxScore
    }

    fun scoreToLevel(score: Float): Int = when {
        score < 1.0f -> 0
        score < 2.0f -> 1
        else         -> 2
    }

    fun levelToAdvice(level: Int): String = when (level) {
        0    -> "Low pollen risk today"
        1    -> "Moderate pollen risk today"
        else -> "High pollen risk today"
    }

    fun computeContributions(pollen: DailyPollen, weights: UserWeights): List<String> =
        PollenType.entries
            .map { type -> type to type.normalise(pollen.getRaw(type)) * weights.getWeight(type) }
            .filter { (_, contribution) -> contribution > 0.05f }
            .sortedByDescending { (_, contribution) -> contribution }
            .map { (type, _) -> type.displayName }
}
