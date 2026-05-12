package com.drourke.allergybuster.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drourke.allergybuster.data.local.db.entity.PollenForecastEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PollenForecastDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(forecast: PollenForecastEntity)

    @Query("SELECT * FROM pollen_forecast WHERE date = :date")
    suspend fun getForDate(date: String): PollenForecastEntity?

    @Query("SELECT * FROM pollen_forecast ORDER BY date DESC LIMIT 1")
    suspend fun getMostRecent(): PollenForecastEntity?

    @Query("SELECT * FROM pollen_forecast ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<PollenForecastEntity>>

    @Query("DELETE FROM pollen_forecast WHERE date < :cutoff")
    suspend fun deleteOlderThan(cutoff: String)
}
