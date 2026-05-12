package com.drourke.allergybuster.di

import android.content.Context
import androidx.room.Room
import com.drourke.allergybuster.data.local.db.AllergyDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): AllergyDatabase =
        Room.databaseBuilder(ctx, AllergyDatabase::class.java, "allergy.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun providePollenForecastDao(db: AllergyDatabase) = db.pollenForecastDao()
    @Provides fun provideRecommendationDao(db: AllergyDatabase) = db.recommendationDao()
    @Provides fun provideDailyFeedbackDao(db: AllergyDatabase)  = db.dailyFeedbackDao()
    @Provides fun provideUserWeightsDao(db: AllergyDatabase)    = db.userWeightsDao()
}
