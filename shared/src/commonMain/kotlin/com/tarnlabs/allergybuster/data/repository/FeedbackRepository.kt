package com.tarnlabs.allergybuster.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOne
import com.tarnlabs.allergybuster.data.local.db.AllergyBusterDatabase
import com.tarnlabs.allergybuster.domain.model.DailyFeedback
import com.tarnlabs.allergybuster.domain.model.UserWeights
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

class FeedbackRepository(private val db: AllergyBusterDatabase) {

    suspend fun saveFeedback(date: String, severity: Int) {
        db.dailyFeedbackQueries.insertOrReplace(
            date            = date,
            severity        = severity.toLong(),
            recordedAt      = Clock.System.now().toEpochMilliseconds(),
            bayesianApplied = 0L
        )
    }

    suspend fun getFeedbackForDate(date: String): DailyFeedback? =
        db.dailyFeedbackQueries.getForDate(date).executeAsOneOrNull()?.toDomain()

    suspend fun getWeights(): UserWeights =
        db.userWeightsQueries.get().executeAsOneOrNull()?.toDomain() ?: UserWeights()

    suspend fun saveWeights(weights: UserWeights) {
        db.userWeightsQueries.upsert(
            alderWeight   = weights.alderWeight.toDouble(),
            birchWeight   = weights.birchWeight.toDouble(),
            grassWeight   = weights.grassWeight.toDouble(),
            mugwortWeight = weights.mugwortWeight.toDouble(),
            oliveWeight   = weights.oliveWeight.toDouble(),
            ragweedWeight = weights.ragweedWeight.toDouble(),
            updatedAt     = Clock.System.now().toEpochMilliseconds()
        )
    }

    fun observeRecentFeedback(limit: Int = 90): Flow<List<DailyFeedback>> =
        db.dailyFeedbackQueries.observeRecent(limit.toLong())
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    fun observeFeedbackCount(): Flow<Long> =
        db.dailyFeedbackQueries.observeCount()
            .asFlow()
            .mapToOne(Dispatchers.Default)

    fun observeWeights(): Flow<UserWeights?> =
        db.userWeightsQueries.observe()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.firstOrNull()?.toDomain() }

    suspend fun getPendingBayesianUpdates(today: String): List<DailyFeedback> =
        db.dailyFeedbackQueries.getPendingBayesianUpdates(today)
            .executeAsList()
            .map { it.toDomain() }

    suspend fun markBayesianApplied(date: String) =
        db.dailyFeedbackQueries.markBayesianApplied(date)
}

private fun com.tarnlabs.allergybuster.data.local.db.Daily_feedback.toDomain() = DailyFeedback(
    date            = date,
    severity        = severity.toInt(),
    recordedAt      = recordedAt,
    bayesianApplied = bayesianApplied != 0L
)

private fun com.tarnlabs.allergybuster.data.local.db.User_weights.toDomain() = UserWeights(
    alderWeight   = alderWeight.toFloat(),
    birchWeight   = birchWeight.toFloat(),
    grassWeight   = grassWeight.toFloat(),
    mugwortWeight = mugwortWeight.toFloat(),
    oliveWeight   = oliveWeight.toFloat(),
    ragweedWeight = ragweedWeight.toFloat(),
    updatedAt     = updatedAt
)
