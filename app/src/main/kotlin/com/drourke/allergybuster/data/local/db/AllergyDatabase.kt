package com.drourke.allergybuster.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.drourke.allergybuster.data.local.db.converter.Converters
import com.drourke.allergybuster.data.local.db.dao.DailyFeedbackDao
import com.drourke.allergybuster.data.local.db.dao.PollenForecastDao
import com.drourke.allergybuster.data.local.db.dao.RecommendationDao
import com.drourke.allergybuster.data.local.db.dao.UserWeightsDao
import com.drourke.allergybuster.data.local.db.entity.DailyFeedbackEntity
import com.drourke.allergybuster.data.local.db.entity.PollenForecastEntity
import com.drourke.allergybuster.data.local.db.entity.RecommendationEntity
import com.drourke.allergybuster.data.local.db.entity.UserWeightsEntity

@Database(
    entities = [
        PollenForecastEntity::class,
        RecommendationEntity::class,
        DailyFeedbackEntity::class,
        UserWeightsEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AllergyDatabase : RoomDatabase() {
    abstract fun pollenForecastDao(): PollenForecastDao
    abstract fun recommendationDao(): RecommendationDao
    abstract fun dailyFeedbackDao(): DailyFeedbackDao
    abstract fun userWeightsDao(): UserWeightsDao
}
