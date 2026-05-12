package com.drourke.allergybuster.data.remote

import com.drourke.allergybuster.data.remote.dto.AirQualityResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenMeteoApi {

    @GET("v1/air-quality")
    suspend fun getAirQuality(
        @Query("latitude")      latitude: Double,
        @Query("longitude")     longitude: Double,
        @Query("hourly")        hourly: String,
        @Query("forecast_days") forecastDays: Int,
        @Query("timezone")      timezone: String
    ): AirQualityResponse
}
