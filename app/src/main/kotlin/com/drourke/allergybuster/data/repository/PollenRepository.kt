package com.drourke.allergybuster.data.repository

import com.drourke.allergybuster.data.local.db.dao.PollenForecastDao
import com.drourke.allergybuster.data.local.db.entity.toDomain
import com.drourke.allergybuster.data.remote.OpenMeteoApi
import com.drourke.allergybuster.data.remote.dto.toDailyEntities
import com.drourke.allergybuster.domain.model.DailyPollen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private const val HOURLY_FIELDS =
    "alder_pollen,birch_pollen,grass_pollen,mugwort_pollen,olive_pollen,ragweed_pollen"

@Singleton
class PollenRepository @Inject constructor(
    private val api: OpenMeteoApi,
    private val dao: PollenForecastDao
) {
    /** Fetches from network, stores all returned days, returns today's entry. Null on failure. */
    suspend fun fetchAndStore(lat: Double = 54.66, lon: Double = -3.36): DailyPollen? = try {
        val response = api.getAirQuality(
            latitude     = lat,
            longitude    = lon,
            hourly       = HOURLY_FIELDS,
            forecastDays = 4,
            timezone     = "Europe/London"
        )
        val entities = response.toDailyEntities()
        entities.forEach { dao.insertOrReplace(it) }
        val today = LocalDate.now().toString()
        entities.find { it.date == today }?.toDomain()
    } catch (_: IOException) { null }
      catch (_: HttpException) { null }

    suspend fun getCachedForDate(date: String): DailyPollen? =
        dao.getForDate(date)?.toDomain()

    suspend fun getMostRecent(): DailyPollen? =
        dao.getMostRecent()?.toDomain()

    fun observeRecent(limit: Int = 30): Flow<List<DailyPollen>> =
        dao.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    suspend fun pruneOldForecasts() {
        val cutoff = LocalDate.now().minusDays(14).toString()
        dao.deleteOlderThan(cutoff)
    }
}
