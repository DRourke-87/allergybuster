package com.tarnlabs.allergybuster.data.repository

import com.tarnlabs.allergybuster.data.local.db.dao.DailyFeedbackDao
import com.tarnlabs.allergybuster.data.local.db.dao.UserWeightsDao
import com.tarnlabs.allergybuster.data.local.db.entity.DailyFeedbackEntity
import com.tarnlabs.allergybuster.data.local.db.entity.toDomain
import com.tarnlabs.allergybuster.data.local.db.entity.toEntity
import com.tarnlabs.allergybuster.domain.model.UserWeights
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackRepository @Inject constructor(
    private val feedbackDao: DailyFeedbackDao,
    private val weightsDao: UserWeightsDao
) {
    suspend fun saveFeedback(date: String, severity: Int) {
        feedbackDao.insertOrReplace(
            DailyFeedbackEntity(date, severity, System.currentTimeMillis())
        )
    }

    suspend fun getFeedbackForDate(date: String): DailyFeedbackEntity? =
        feedbackDao.getForDate(date)

    suspend fun getWeights(): UserWeights =
        weightsDao.get()?.toDomain() ?: UserWeights()

    suspend fun saveWeights(weights: UserWeights) =
        weightsDao.save(weights.toEntity().copy(updatedAt = System.currentTimeMillis()))

    fun observeRecentFeedback(limit: Int = 90): Flow<List<DailyFeedbackEntity>> =
        feedbackDao.observeRecent(limit)

    fun observeFeedbackCount(): Flow<Int> = feedbackDao.observeFeedbackCount()

    fun observeWeights(): Flow<UserWeights?> = weightsDao.observe().map { it?.toDomain() }

    suspend fun getPendingBayesianUpdates(today: String): List<DailyFeedbackEntity> =
        feedbackDao.getPendingBayesianUpdates(today)

    suspend fun markBayesianApplied(date: String) =
        feedbackDao.markBayesianApplied(date)
}
