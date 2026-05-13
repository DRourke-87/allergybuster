package com.tarnlabs.allergybuster.domain.engine

import com.tarnlabs.allergybuster.data.local.db.entity.UserWeightsEntity
import com.tarnlabs.allergybuster.domain.model.DailyPollen
import com.tarnlabs.allergybuster.domain.model.PollenType
import com.tarnlabs.allergybuster.domain.model.getWeight
import com.tarnlabs.allergybuster.domain.model.withWeight

object BayesianUpdater {

    private const val LEARNING_RATE = 0.15f
    private const val NON_TRIVIAL_THRESHOLD = 0.5f
    private const val MIN_WEIGHT = 0.1f
    private const val MAX_WEIGHT = 5.0f

    /**
     * Adjusts per-pollen-type sensitivity weights based on the difference between
     * what the model predicted (predictedLevel) and what the user actually experienced
     * (actualSeverity). Only updates types that were meaningfully present that day.
     */
    fun updateWeights(
        current: UserWeightsEntity,
        pollen: DailyPollen,
        actualSeverity: Int,     // 0=fine, 1=mild, 2=severe
        predictedLevel: Int      // 0=none, 1=consider, 2=take
    ): UserWeightsEntity {
        val error = (actualSeverity - predictedLevel).toFloat()
        var updated = current

        for (type in PollenType.entries) {
            val norm = type.normalise(pollen.getRaw(type))
            if (norm >= NON_TRIVIAL_THRESHOLD) {
                val delta = LEARNING_RATE * error * (norm / 3.0f)
                val newWeight = (updated.getWeight(type) + delta).coerceIn(MIN_WEIGHT, MAX_WEIGHT)
                updated = updated.withWeight(type, newWeight)
            }
        }
        return updated
    }
}
