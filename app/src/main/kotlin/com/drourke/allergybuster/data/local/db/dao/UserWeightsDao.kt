package com.drourke.allergybuster.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drourke.allergybuster.data.local.db.entity.UserWeightsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserWeightsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(weights: UserWeightsEntity)

    @Query("SELECT * FROM user_weights WHERE id = 1")
    suspend fun get(): UserWeightsEntity?

    @Query("SELECT * FROM user_weights WHERE id = 1")
    fun observe(): Flow<UserWeightsEntity?>
}
