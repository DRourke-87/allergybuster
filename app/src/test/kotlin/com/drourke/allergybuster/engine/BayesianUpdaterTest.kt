package com.drourke.allergybuster.engine

import com.drourke.allergybuster.data.local.db.entity.UserWeightsEntity
import com.drourke.allergybuster.domain.engine.BayesianUpdater
import com.drourke.allergybuster.domain.model.DailyPollen
import com.drourke.allergybuster.domain.model.PollenType
import com.drourke.allergybuster.domain.model.getWeight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BayesianUpdaterTest {

    private val defaultWeights = UserWeightsEntity()
    private val now = System.currentTimeMillis()

    private fun pollen(
        alder: Float = 0f, birch: Float = 0f, grass: Float = 0f,
        mugwort: Float = 0f, olive: Float = 0f, ragweed: Float = 0f
    ) = DailyPollen("2025-06-01", alder, birch, grass, mugwort, olive, ragweed, now)

    @Test fun `no error leaves weights unchanged`() {
        val p = pollen(grass = 50f)  // grass is non-trivial (norm=3)
        val result = BayesianUpdater.updateWeights(defaultWeights, p, actualSeverity = 1, predictedLevel = 1)
        assertEquals(defaultWeights.grassWeight, result.grassWeight, 0.001f)
    }

    @Test fun `under-predicted increases non-trivial weights`() {
        // actual=2, predicted=0 → error=+2; grass norm=3 (high, non-trivial)
        val p = pollen(grass = 50f)
        val result = BayesianUpdater.updateWeights(defaultWeights, p, actualSeverity = 2, predictedLevel = 0)
        assertTrue(result.grassWeight > defaultWeights.grassWeight)
    }

    @Test fun `over-predicted decreases non-trivial weights`() {
        // actual=0, predicted=2 → error=-2; grass norm=3
        val p = pollen(grass = 50f)
        val result = BayesianUpdater.updateWeights(defaultWeights, p, actualSeverity = 0, predictedLevel = 2)
        assertTrue(result.grassWeight < defaultWeights.grassWeight)
    }

    @Test fun `weight clamped at minimum 0_1 after repeated negative feedback`() {
        var weights = defaultWeights
        val p = pollen(grass = 50f)
        repeat(100) {
            weights = BayesianUpdater.updateWeights(weights, p, actualSeverity = 0, predictedLevel = 2)
        }
        assertTrue(weights.grassWeight >= 0.1f)
    }

    @Test fun `weight clamped at maximum 5_0 after repeated positive feedback`() {
        var weights = defaultWeights
        val p = pollen(grass = 50f)
        repeat(100) {
            weights = BayesianUpdater.updateWeights(weights, p, actualSeverity = 2, predictedLevel = 0)
        }
        assertTrue(weights.grassWeight <= 5.0f)
    }

    @Test fun `trivial pollen (norm less than 0_5) is not updated`() {
        // grass at 1 grains/m³ → norm = 1/10 = 0.1 < 0.5 threshold
        val p = pollen(grass = 1f)
        val result = BayesianUpdater.updateWeights(defaultWeights, p, actualSeverity = 2, predictedLevel = 0)
        assertEquals(defaultWeights.grassWeight, result.grassWeight, 0.001f)
    }

    @Test fun `only non-trivial types are updated`() {
        // grass is high (norm=3), birch is zero (norm=0)
        val p = pollen(grass = 50f, birch = 0f)
        val result = BayesianUpdater.updateWeights(defaultWeights, p, actualSeverity = 2, predictedLevel = 0)
        assertTrue(result.grassWeight > defaultWeights.grassWeight)
        assertEquals(defaultWeights.birchWeight, result.birchWeight, 0.001f)
    }

    @Test fun `each type is updated independently`() {
        // Both grass and birch non-trivial
        val p = pollen(grass = 50f, birch = 100f)
        val result = BayesianUpdater.updateWeights(defaultWeights, p, actualSeverity = 2, predictedLevel = 0)
        assertTrue(result.grassWeight > defaultWeights.grassWeight)
        assertTrue(result.birchWeight > defaultWeights.birchWeight)
        // Grass norm=3, Birch norm=3 → equal delta, so both increase equally
        assertEquals(result.grassWeight, result.birchWeight, 0.001f)
    }

    @Test fun `delta magnitude proportional to pollen normalisation`() {
        // high grass (norm=3) should produce larger delta than moderate grass (norm=1)
        val highPollen = pollen(grass = 50f)
        val modPollen  = pollen(grass = 10f) // norm=1 exactly
        val highResult = BayesianUpdater.updateWeights(defaultWeights, highPollen, actualSeverity = 2, predictedLevel = 0)
        val modResult  = BayesianUpdater.updateWeights(defaultWeights, modPollen,  actualSeverity = 2, predictedLevel = 0)
        assertTrue(highResult.grassWeight > modResult.grassWeight)
    }
}
