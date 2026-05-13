package com.tarnlabs.allergybuster.domain.model

enum class PollenType(
    val apiField: String,
    val displayName: String,
    val low: Float,
    val moderate: Float,
    val high: Float
) {
    ALDER  ("alder_pollen",   "Alder",   10f, 50f, 100f),
    BIRCH  ("birch_pollen",   "Birch",   10f, 50f, 100f),
    GRASS  ("grass_pollen",   "Grass",   10f, 30f,  50f),
    MUGWORT("mugwort_pollen", "Mugwort",  5f, 15f,  30f),
    OLIVE  ("olive_pollen",   "Olive",   10f, 50f, 100f),
    RAGWEED("ragweed_pollen", "Ragweed",  5f, 15f,  30f);

    /** Piecewise-linear normalisation: raw grains/m³ → 0.0–3.0 */
    fun normalise(raw: Float): Float {
        val result = when {
            raw < low      -> raw / low
            raw < moderate -> 1f + (raw - low) / (moderate - low)
            raw < high     -> 2f + (raw - moderate) / (high - moderate)
            else           -> 3f
        }
        return result.coerceIn(0f, 3f)
    }
}
