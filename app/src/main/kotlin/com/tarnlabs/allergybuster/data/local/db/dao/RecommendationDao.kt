package com.tarnlabs.allergybuster.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.tarnlabs.allergybuster.data.local.db.entity.RecommendationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecommendationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(rec: RecommendationEntity)

    @Query("SELECT * FROM recommendation WHERE date = :date")
    suspend fun getForDate(date: String): RecommendationEntity?

    @Query("SELECT * FROM recommendation ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 90): Flow<List<RecommendationEntity>>
}
