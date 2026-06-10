package com.tarnlabs.allergybuster.domain.model

data class PlaceResult(
    val name: String,
    val region: String,
    val country: String,
    val latitude: Double,
    val longitude: Double
) {
    val displayName: String
        get() = listOf(name, region, country)
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(", ")
}
