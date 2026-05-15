package com.tarnlabs.allergybuster.domain.model

enum class PollenType(
    val apiField: String,
    val displayName: String,
    val low: Float,
    val moderate: Float,
    val high: Float,
    val icon: String,
    val seasonality: String,
    val crossReactions: String
) {
    ALDER("alder_pollen", "Alder", 10f, 50f, 100f,
        "🌳", "Late winter, early spring",
        "Birch, Hazel, Hornbeam, Beech, Willow and Oak pollen"),
    BIRCH("birch_pollen", "Birch", 10f, 50f, 100f,
        "🌲", "Spring",
        "Apple, Peach, Carrot, Celery, Hazel, Alder and Olive pollen"),
    GRASS("grass_pollen", "Grass", 10f, 30f, 50f,
        "🌾", "Late spring through summer",
        "Cereal crops — wheat, rye and oats"),
    MUGWORT("mugwort_pollen", "Mugwort", 5f, 15f, 30f,
        "🌿", "Late summer, early autumn",
        "Ragweed, Celery, Carrot and various spices"),
    OLIVE("olive_pollen", "Olive", 10f, 50f, 100f,
        "🫒", "Late spring, early summer",
        "Ash and Privet pollen"),
    RAGWEED("ragweed_pollen", "Ragweed", 5f, 15f, 30f,
        "🌻", "Late summer through autumn",
        "Mugwort, Sunflower and Chrysanthemum pollen");

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

    companion object {
        fun fromDisplayName(name: String): PollenType? = entries.find { it.displayName == name }
    }
}
