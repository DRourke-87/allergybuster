package com.tarnlabs.allergybuster.data.remote.dto

import com.tarnlabs.allergybuster.domain.model.PlaceResult
import kotlinx.serialization.Serializable

@Serializable
data class GeocodingResponse(
    val results: List<GeocodingResult> = emptyList()
)

@Serializable
data class GeocodingResult(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String = "",
    val admin1: String = ""
)

fun GeocodingResponse.toPlaceResults(): List<PlaceResult> = results.map {
    PlaceResult(
        name      = it.name,
        region    = it.admin1,
        country   = it.country,
        latitude  = it.latitude,
        longitude = it.longitude
    )
}
