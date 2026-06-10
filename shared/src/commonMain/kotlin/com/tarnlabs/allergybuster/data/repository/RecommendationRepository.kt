package com.tarnlabs.allergybuster.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.tarnlabs.allergybuster.data.local.db.AllergyBusterDatabase
import com.tarnlabs.allergybuster.domain.model.Recommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RecommendationRepository(private val db: AllergyBusterDatabase) {

    suspend fun save(rec: Recommendation) {
        db.recommendationQueries.insertOrReplace(
            date            = rec.date,
            level           = rec.level.toLong(),
            score           = rec.score.toDouble(),
            advice          = rec.advice,
            topContributors = Json.encodeToString(rec.topContributors),
            computedAt      = rec.computedAt,
            isStale         = if (rec.isStale) 1L else 0L,
            locationName    = rec.locationName
        )
    }

    suspend fun getForDate(date: String): Recommendation? =
        db.recommendationQueries.getForDate(date).executeAsOneOrNull()?.toDomain()

    fun observeRecent(limit: Int = 90): Flow<List<Recommendation>> =
        db.recommendationQueries.observeRecent(limit.toLong())
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.mapNotNull { runCatching { it.toDomain() }.getOrNull() } }
            .catch { emit(emptyList()) }
}

private fun com.tarnlabs.allergybuster.data.local.db.Recommendation.toDomain() = Recommendation(
    date            = date,
    level           = level.toInt(),
    score           = score.toFloat(),
    advice          = advice,
    topContributors = try {
        Json.decodeFromString(topContributors)
    } catch (e: Exception) {
        println("AllergyBuster: corrupt topContributors for $date: ${e.message}")
        emptyList()
    },
    computedAt      = computedAt,
    isStale         = isStale != 0L,
    locationName    = locationName
)
