package com.drourke.allergybuster.data.repository

import com.drourke.allergybuster.data.local.db.dao.DailyFeedbackDao
import com.drourke.allergybuster.data.local.db.dao.UserWeightsDao
import com.drourke.allergybuster.data.local.db.entity.DailyFeedbackEntity
import com.drourke.allergybuster.data.local.db.entity.UserWeightsEntity
import kotlinx.coroutines.flow.Flow
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

    suspend fun getWeights(): UserWeightsEntity =
        weightsDao.get() ?: UserWeightsEntity()

    suspend fun saveWeights(weights: UserWeightsEntity) =
        weightsDao.save(weights.copy(updatedAt = System.currentTimeMillis()))

    fun observeRecentFeedback(limit: Int = 90): Flow<List<DailyFeedbackEntity>> =
        feedbackDao.observeRecent(limit)

    fun observeFeedbackCount(): Flow<Int> = feedbackDao.observeFeedbackCount()

    fun observeWeights() = weightsDao.observe()
}
