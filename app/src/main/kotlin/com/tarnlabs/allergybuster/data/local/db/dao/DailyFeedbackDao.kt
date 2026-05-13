package com.tarnlabs.allergybuster.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tarnlabs.allergybuster.data.local.db.entity.DailyFeedbackEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyFeedbackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(feedback: DailyFeedbackEntity)

    @Query("SELECT * FROM daily_feedback WHERE date = :date")
    suspend fun getForDate(date: String): DailyFeedbackEntity?

    @Query("SELECT * FROM daily_feedback ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 90): Flow<List<DailyFeedbackEntity>>

    @Query("SELECT COUNT(*) FROM daily_feedback")
    fun observeFeedbackCount(): Flow<Int>
}
