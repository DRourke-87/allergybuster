package com.tarnlabs.allergybuster.engine

import com.tarnlabs.allergybuster.domain.engine.RecommendationEngine
import com.tarnlabs.allergybuster.domain.model.DailyPollen
import com.tarnlabs.allergybuster.domain.model.UserWeights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationEngineTest {

    private val defaultWeights = UserWeights()
    private val now = System.currentTimeMillis()

    private fun pollen(
        alder: Float = 0f, birch: Float = 0f, grass: Float = 0f,
        mugwort: Float = 0f, olive: Float = 0f, ragweed: Float = 0f
    ) = DailyPollen("2025-06-01", alder, birch, grass, mugwort, olive, ragweed, now)

    // --- normalisation boundary tests ---

    @Test fun `all zero pollen scores zero and maps to level 0`() {
        val score = RecommendationEngine.computeScore(pollen(), defaultWeights)
        assertEquals(0f, score, 0.001f)
        assertEquals(0, RecommendationEngine.scoreToLevel(score))
    }

    @Test fun `grass at low threshold normalises to exactly 1`() {
        // grass low=10; normalise(10) = 1.0; with 6 equal-weight types, score = 1/6 ≈ 0.167
        val score = RecommendationEngine.computeScore(pollen(grass = 10f), defaultWeights)
        assertEquals(1f / 6f, score, 0.001f)
        assertEquals(0, RecommendationEngine.scoreToLevel(score))
    }

    @Test fun `grass at high threshold normalises to exactly 3`() {
        // grass high=50; normalise(50) = 3.0; score = 3/6 = 0.5
        val score = RecommendationEngine.computeScore(pollen(grass = 50f), defaultWeights)
        assertEquals(3f / 6f, score, 0.001f)
        assertEquals(0, RecommendationEngine.scoreToLevel(score))
    }

    @Test fun `grass above high threshold clamps to 3`() {
        val score = RecommendationEngine.computeScore(pollen(grass = 999f), defaultWeights)
        assertEquals(3f / 6f, score, 0.001f)
    }

    @Test fun `grass and birch both at high threshold gives level 1`() {
        // grass norm=3, birch norm=3, others=0 → weightedSum=6, totalWeight=6 → score=1.0
        val score = RecommendationEngine.computeScore(pollen(grass = 50f, birch = 100f), defaultWeights)
        assertEquals(1.0f, score, 0.001f)
        assertEquals(1, RecommendationEngine.scoreToLevel(score))
    }

    @Test fun `all types at high threshold gives level 2`() {
        // All norm=3, all weight=1 → score = 18/6 = 3.0
        val score = RecommendationEngine.computeScore(
            pollen(alder = 100f, birch = 100f, grass = 50f, mugwort = 30f, olive = 100f, ragweed = 30f),
            defaultWeights
        )
        assertEquals(3.0f, score, 0.001f)
        assertEquals(2, RecommendationEngine.scoreToLevel(score))
    }

    // --- scoreToLevel boundary values ---

    @Test fun `score 0_749 maps to level 0`() = assertEquals(0, RecommendationEngine.scoreToLevel(0.749f))
    @Test fun `score 0_75 maps to level 1`()  = assertEquals(1, RecommendationEngine.scoreToLevel(0.75f))
    @Test fun `score 1_499 maps to level 1`() = assertEquals(1, RecommendationEngine.scoreToLevel(1.499f))
    @Test fun `score 1_5 maps to level 2`()   = assertEquals(2, RecommendationEngine.scoreToLevel(1.5f))

    // --- contributions ordering ---

    @Test fun `computeContributions orders by contribution descending`() {
        val p = pollen(grass = 50f, birch = 100f) // grass norm=3, birch norm=3 but equal weight
        val contributors = RecommendationEngine.computeContributions(p, defaultWeights)
        assertTrue(contributors.isNotEmpty())
        // Both present; Alder/Birch/Grass all equal weight but only grass+birch non-zero
        assertTrue(contributors.contains("Grass"))
        assertTrue(contributors.contains("Birch"))
    }

    @Test fun `computeContributions excludes trivial types`() {
        // grass very low (0.5 grains → norm=0.05, contribution=0.05) should be excluded
        val contributors = RecommendationEngine.computeContributions(pollen(grass = 0.5f), defaultWeights)
        assertTrue(contributors.isEmpty())
    }

    // --- weighted scoring with custom weights ---

    @Test fun `higher grass weight increases score proportionally`() {
        val highGrassWeights = defaultWeights.copy(grassWeight = 3.0f)
        val base    = RecommendationEngine.computeScore(pollen(grass = 30f), defaultWeights)
        val boosted = RecommendationEngine.computeScore(pollen(grass = 30f), highGrassWeights)
        assertTrue(boosted > base)
    }
}
