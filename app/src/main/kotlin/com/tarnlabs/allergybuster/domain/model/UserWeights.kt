package com.tarnlabs.allergybuster.domain.model

import com.tarnlabs.allergybuster.data.local.db.entity.UserWeightsEntity

/** Extension helpers so engine logic stays decoupled from the Room entity shape. */

fun UserWeightsEntity.getWeight(type: PollenType): Float = when (type) {
    PollenType.ALDER   -> alderWeight
    PollenType.BIRCH   -> birchWeight
    PollenType.GRASS   -> grassWeight
    PollenType.MUGWORT -> mugwortWeight
    PollenType.OLIVE   -> oliveWeight
    PollenType.RAGWEED -> ragweedWeight
}

fun UserWeightsEntity.withWeight(type: PollenType, value: Float): UserWeightsEntity = when (type) {
    PollenType.ALDER   -> copy(alderWeight   = value)
    PollenType.BIRCH   -> copy(birchWeight   = value)
    PollenType.GRASS   -> copy(grassWeight   = value)
    PollenType.MUGWORT -> copy(mugwortWeight = value)
    PollenType.OLIVE   -> copy(oliveWeight   = value)
    PollenType.RAGWEED -> copy(ragweedWeight = value)
}
