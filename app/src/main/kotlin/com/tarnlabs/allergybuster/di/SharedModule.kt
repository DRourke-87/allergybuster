package com.tarnlabs.allergybuster.di

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import com.tarnlabs.allergybuster.data.local.db.AllergyBusterDatabase
import com.tarnlabs.allergybuster.data.local.db.DatabaseDriverFactory
import com.tarnlabs.allergybuster.data.remote.GeocodingApiClient
import com.tarnlabs.allergybuster.data.remote.OpenMeteoApiClient
import com.tarnlabs.allergybuster.data.repository.FeedbackRepository
import com.tarnlabs.allergybuster.data.repository.PollenRepository
import com.tarnlabs.allergybuster.data.repository.RecommendationRepository
import com.tarnlabs.allergybuster.domain.usecase.ApplyDailyBayesianUseCase
import com.tarnlabs.allergybuster.domain.usecase.CheckLocationOutlookUseCase
import com.tarnlabs.allergybuster.domain.usecase.ComputeRecommendationUseCase
import com.tarnlabs.allergybuster.domain.usecase.ObserveOutlookUseCase
import com.tarnlabs.allergybuster.domain.usecase.SearchPlacesUseCase
import com.tarnlabs.allergybuster.domain.usecase.SubmitFeedbackUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SharedModule {

    @Provides @Singleton
    fun provideDatabaseDriverFactory(@ApplicationContext ctx: Context) =
        DatabaseDriverFactory(ctx)

    @Provides @Singleton
    fun provideSqlDriver(factory: DatabaseDriverFactory): SqlDriver =
        factory.createDriver()

    @Provides @Singleton
    fun provideDatabase(driver: SqlDriver): AllergyBusterDatabase =
        AllergyBusterDatabase(driver)

    @Provides @Singleton
    fun provideOpenMeteoApiClient() = OpenMeteoApiClient()

    @Provides @Singleton
    fun providePollenRepository(db: AllergyBusterDatabase, api: OpenMeteoApiClient) =
        PollenRepository(db, api)

    @Provides @Singleton
    fun provideFeedbackRepository(db: AllergyBusterDatabase) =
        FeedbackRepository(db)

    @Provides @Singleton
    fun provideRecommendationRepository(db: AllergyBusterDatabase) =
        RecommendationRepository(db)

    @Provides @Singleton
    fun provideApplyDailyBayesianUseCase(
        feedbackRepository: FeedbackRepository,
        pollenRepository: PollenRepository,
        recommendationRepository: RecommendationRepository
    ) = ApplyDailyBayesianUseCase(feedbackRepository, pollenRepository, recommendationRepository)

    @Provides @Singleton
    fun provideComputeRecommendationUseCase(feedbackRepository: FeedbackRepository) =
        ComputeRecommendationUseCase(feedbackRepository)

    @Provides @Singleton
    fun provideSubmitFeedbackUseCase(feedbackRepository: FeedbackRepository) =
        SubmitFeedbackUseCase(feedbackRepository)

    @Provides @Singleton
    fun provideObserveOutlookUseCase(
        pollenRepository: PollenRepository,
        feedbackRepository: FeedbackRepository
    ) = ObserveOutlookUseCase(pollenRepository, feedbackRepository)

    @Provides @Singleton
    fun provideGeocodingApiClient() = GeocodingApiClient()

    @Provides @Singleton
    fun provideSearchPlacesUseCase(api: GeocodingApiClient) =
        SearchPlacesUseCase(api)

    @Provides @Singleton
    fun provideCheckLocationOutlookUseCase(
        api: OpenMeteoApiClient,
        feedbackRepository: FeedbackRepository
    ) = CheckLocationOutlookUseCase(api, feedbackRepository)
}
