package com.tarnlabs.allergybuster.data.remote.dto

import com.tarnlabs.allergybuster.domain.model.DailyPollen
import kotlinx.datetime.Clock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AirQualityResponse(
    val hourly: HourlyData
)

@Serializable
data class HourlyData(
    val time: List<String>,
    @SerialName("alder_pollen")   val alderPollen:   List<Float?>,
    @SerialName("birch_pollen")   val birchPollen:   List<Float?>,
    @SerialName("grass_pollen")   val grassPollen:   List<Float?>,
    @SerialName("mugwort_pollen") val mugwortPollen: List<Float?>,
    @SerialName("olive_pollen")   val olivePollen:   List<Float?>,
    @SerialName("ragweed_pollen") val ragweedPollen: List<Float?>
)

fun AirQualityResponse.toDailyPollen(): List<DailyPollen> {
    val fetchedAt = Clock.System.now().toEpochMilliseconds()

    val indexesByDate = hourly.time
        .mapIndexed { i, timeStr -> timeStr.substring(0, 10) to i }
        .groupBy({ it.first }, { it.second })

    return indexesByDate.map { (date, indices) ->
        fun List<Float?>.dailyMax() = indices.mapNotNull { getOrNull(it) }.maxOrNull() ?: 0f

        DailyPollen(
            date       = date,
            alderMax   = hourly.alderPollen.dailyMax(),
            birchMax   = hourly.birchPollen.dailyMax(),
            grassMax   = hourly.grassPollen.dailyMax(),
            mugwortMax = hourly.mugwortPollen.dailyMax(),
            oliveMax   = hourly.olivePollen.dailyMax(),
            ragweedMax = hourly.ragweedPollen.dailyMax(),
            fetchedAt  = fetchedAt
        )
    }
}
