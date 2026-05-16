package com.tarnlabs.allergybuster.domain.model

data class UserWeights(
    val alderWeight: Float   = 1.0f,
    val birchWeight: Float   = 1.0f,
    val grassWeight: Float   = 1.0f,
    val mugwortWeight: Float = 1.0f,
    val oliveWeight: Float   = 1.0f,
    val ragweedWeight: Float = 1.0f,
    val updatedAt: Long      = 0L
)

fun UserWeights.getWeight(type: PollenType): Float = when (type) {
    PollenType.ALDER   -> alderWeight
    PollenType.BIRCH   -> birchWeight
    PollenType.GRASS   -> grassWeight
    PollenType.MUGWORT -> mugwortWeight
    PollenType.OLIVE   -> oliveWeight
    PollenType.RAGWEED -> ragweedWeight
}

fun UserWeights.withWeight(type: PollenType, value: Float): UserWeights = when (type) {
    PollenType.ALDER   -> copy(alderWeight   = value)
    PollenType.BIRCH   -> copy(birchWeight   = value)
    PollenType.GRASS   -> copy(grassWeight   = value)
    PollenType.MUGWORT -> copy(mugwortWeight = value)
    PollenType.OLIVE   -> copy(oliveWeight   = value)
    PollenType.RAGWEED -> copy(ragweedWeight = value)
}
