package com.drourke.allergybuster.domain.engine

import com.drourke.allergybuster.data.local.db.entity.UserWeightsEntity
import com.drourke.allergybuster.domain.model.DailyPollen
import com.drourke.allergybuster.domain.model.PollenType
import com.drourke.allergybuster.domain.model.getWeight

object RecommendationEngine {

    fun computeScore(pollen: DailyPollen, weights: UserWeightsEntity): Float {
        var weightedSum = 0f
        var totalWeight = 0f
        for (type in PollenType.entries) {
            val norm = type.normalise(pollen.getRaw(type))
            val weight = weights.getWeight(type)
            weightedSum += norm * weight
            totalWeight += weight
        }
        return if (totalWeight == 0f) 0f else weightedSum / totalWeight
    }

    fun scoreToLevel(score: Float): Int = when {
        score < 0.75f -> 0
        score < 1.50f -> 1
        else          -> 2
    }

    fun levelToAdvice(level: Int): String = when (level) {
        0    -> "No antihistamine needed today"
        1    -> "Consider taking antihistamine today"
        else -> "Take antihistamine today"
    }

    fun computeContributions(pollen: DailyPollen, weights: UserWeightsEntity): List<String> =
        PollenType.entries
            .map { type -> type to type.normalise(pollen.getRaw(type)) * weights.getWeight(type) }
            .filter { (_, contribution) -> contribution > 0.05f }
            .sortedByDescending { (_, contribution) -> contribution }
            .map { (type, _) -> type.displayName }
}
