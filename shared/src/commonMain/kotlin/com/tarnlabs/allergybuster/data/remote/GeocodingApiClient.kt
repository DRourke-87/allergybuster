package com.tarnlabs.allergybuster.data.remote

import com.tarnlabs.allergybuster.data.remote.dto.GeocodingResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

private const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"

class GeocodingApiClient {

    private val client = createHttpClient()

    suspend fun search(query: String, count: Int = 5): GeocodingResponse =
        client.get(GEOCODING_URL) {
            parameter("name", query)
            parameter("count", count)
            parameter("language", "en")
            parameter("format", "json")
        }.body()
}
