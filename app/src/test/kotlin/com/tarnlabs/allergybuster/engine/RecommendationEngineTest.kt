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
        // grass low=10; normalise(10) = 1.0; max score = 1.0 → level 1 (Moderate)
        val score = RecommendationEngine.computeScore(pollen(grass = 10f), defaultWeights)
        assertEquals(1.0f, score, 0.001f)
        assertEquals(1, RecommendationEngine.scoreToLevel(score))
    }

    @Test fun `grass at high threshold normalises to exactly 3`() {
        // grass high=50; normalise(50) = 3.0; max score = 3.0 → level 2 (High)
        val score = RecommendationEngine.computeScore(pollen(grass = 50f), defaultWeights)
        assertEquals(3.0f, score, 0.001f)
        assertEquals(2, RecommendationEngine.scoreToLevel(score))
    }

    @Test fun `grass above high threshold clamps to 3`() {
        val score = RecommendationEngine.computeScore(pollen(grass = 999f), defaultWeights)
        assertEquals(3.0f, score, 0.001f)
    }

    @Test fun `grass and birch both at high threshold gives level 2`() {
        // grass norm=3, birch norm=3, max=3.0 → level 2 (High)
        val score = RecommendationEngine.computeScore(pollen(grass = 50f, birch = 100f), defaultWeights)
        assertEquals(3.0f, score, 0.001f)
        assertEquals(2, RecommendationEngine.scoreToLevel(score))
    }

    @Test fun `all types at high threshold gives level 2`() {
        // All norm=3, all weight=1 → max score = 3.0
        val score = RecommendationEngine.computeScore(
            pollen(alder = 100f, birch = 100f, grass = 50f, mugwort = 30f, olive = 100f, ragweed = 30f),
            defaultWeights
        )
        assertEquals(3.0f, score, 0.001f)
        assertEquals(2, RecommendationEngine.scoreToLevel(score))
    }

    // --- scoreToLevel boundary values ---

    @Test fun `score 0_999 maps to level 0`() = assertEquals(0, RecommendationEngine.scoreToLevel(0.999f))
    @Test fun `score 1_0 maps to level 1`()   = assertEquals(1, RecommendationEngine.scoreToLevel(1.0f))
    @Test fun `score 1_999 maps to level 1`() = assertEquals(1, RecommendationEngine.scoreToLevel(1.999f))
    @Test fun `score 2_0 maps to level 2`()   = assertEquals(2, RecommendationEngine.scoreToLevel(2.0f))

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

    // --- active pollen levels (per-type, weight-independent) ---

    @Test fun `activePollenLevels excludes zero-reading types`() {
        val active = RecommendationEngine.activePollenLevels(pollen(grass = 20f, birch = 60f))
        assertEquals(setOf("Grass", "Birch"), active.map { it.type.displayName }.toSet())
    }

    @Test fun `activePollenLevels uses each type's own baseline`() {
        // 40 grains/m³: birch (moderate=50) is below moderate → norm=1.75 → level 1;
        //               grass (moderate=30) is above moderate → norm=2.5 → level 2
        val active = RecommendationEngine.activePollenLevels(pollen(grass = 40f, birch = 40f))
        val birch = active.first { it.type.displayName == "Birch" }
        val grass = active.first { it.type.displayName == "Grass" }
        assertEquals(1, birch.level)
        assertEquals(2, grass.level)
    }

    @Test fun `activePollenLevels sorts by normalised value descending`() {
        // grass 60 → norm 3.0, birch 30 → norm ~1.5
        val active = RecommendationEngine.activePollenLevels(pollen(grass = 60f, birch = 30f))
        assertEquals("Grass", active.first().type.displayName)
        assertTrue(active[0].norm >= active[1].norm)
    }

    // --- weighted scoring with custom weights ---

    @Test fun `higher grass weight increases score proportionally`() {
        val highGrassWeights = defaultWeights.copy(grassWeight = 3.0f)
        val base    = RecommendationEngine.computeScore(pollen(grass = 30f), defaultWeights)
        val boosted = RecommendationEngine.computeScore(pollen(grass = 30f), highGrassWeights)
        assertTrue(boosted > base)
    }
}
