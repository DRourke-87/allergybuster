package com.tarnlabs.allergybuster.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.tarnlabs.allergybuster.data.local.db.AllergyBusterDatabase
import com.tarnlabs.allergybuster.data.remote.OpenMeteoApiClient
import com.tarnlabs.allergybuster.data.remote.dto.toDailyPollen
import com.tarnlabs.allergybuster.domain.model.DailyPollen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

private const val HOURLY_FIELDS =
    "alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen"

class PollenRepository(
    private val db: AllergyBusterDatabase,
    private val api: OpenMeteoApiClient
) {
    /** Fetches from network, stores all returned days, returns today's entry. Null on failure. */
    suspend fun fetchAndStore(lat: Double = 54.66, lon: Double = -3.36): DailyPollen? = try {
        val response = api.getAirQuality(lat, lon, HOURLY_FIELDS, 4, "Europe/London")
        val pollenList = response.toDailyPollen()
        pollenList.forEach { pollen ->
            db.pollenForecastQueries.insertOrReplace(
                date       = pollen.date,
                alderMax   = pollen.alderMax.toDouble(),
                birchMax   = pollen.birchMax.toDouble(),
                grassMax   = pollen.grassMax.toDouble(),
                mugwortMax = pollen.mugwortMax.toDouble(),
                oliveMax   = pollen.oliveMax.toDouble(),
                ragweedMax = pollen.ragweedMax.toDouble(),
                fetchedAt  = pollen.fetchedAt
            )
        }
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        pollenList.find { it.date == today }
    } catch (_: Exception) { null }

    suspend fun getCachedForDate(date: String): DailyPollen? =
        db.pollenForecastQueries.getForDate(date).executeAsOneOrNull()?.toDomain()

    suspend fun getMostRecent(): DailyPollen? =
        db.pollenForecastQueries.getMostRecent().executeAsOneOrNull()?.toDomain()

    fun observeRecent(limit: Int = 30): Flow<List<DailyPollen>> =
        db.pollenForecastQueries.observeRecent(limit.toLong())
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    suspend fun pruneOldForecasts() {
        val cutoff = Clock.System.todayIn(TimeZone.currentSystemDefault())
            .minus(14, DateTimeUnit.DAY)
            .toString()
        db.pollenForecastQueries.deleteOlderThan(cutoff)
    }
}

private fun com.tarnlabs.allergybuster.data.local.db.Pollen_forecast.toDomain() = DailyPollen(
    date       = date,
    alderMax   = alderMax.toFloat(),
    birchMax   = birchMax.toFloat(),
    grassMax   = grassMax.toFloat(),
    mugwortMax = mugwortMax.toFloat(),
    oliveMax   = oliveMax.toFloat(),
    ragweedMax = ragweedMax.toFloat(),
    fetchedAt  = fetchedAt
)
