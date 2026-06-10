package com.tarnlabs.allergybuster.data.remote

import com.tarnlabs.allergybuster.data.remote.dto.AirQualityResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

private const val BASE_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"

class OpenMeteoApiClient {

    companion object {
        const val POLLEN_HOURLY_FIELDS =
            "alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen"
    }

    private val client = createHttpClient()

    suspend fun getAirQuality(
        latitude: Double,
        longitude: Double,
        hourly: String,
        forecastDays: Int,
        timezone: String
    ): AirQualityResponse = client.get(BASE_URL) {
        parameter("latitude", latitude)
        parameter("longitude", longitude)
        parameter("hourly", hourly)
        parameter("forecast_days", forecastDays)
        parameter("timezone", timezone)
    }.body()
}
