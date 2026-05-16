package com.tarnlabs.allergybuster.domain.model

data class DailyPollen(
    val date: String,           // "YYYY-MM-DD"
    val alderMax: Float,
    val birchMax: Float,
    val grassMax: Float,
    val mugwortMax: Float,
    val oliveMax: Float,
    val ragweedMax: Float,
    val fetchedAt: Long
) {
    fun getRaw(type: PollenType): Float = when (type) {
        PollenType.ALDER   -> alderMax
        PollenType.BIRCH   -> birchMax
        PollenType.GRASS   -> grassMax
        PollenType.MUGWORT -> mugwortMax
        PollenType.OLIVE   -> oliveMax
        PollenType.RAGWEED -> ragweedMax
    }
}
