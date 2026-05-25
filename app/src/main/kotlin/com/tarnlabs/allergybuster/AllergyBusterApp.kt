package com.tarnlabs.allergybuster

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.tarnlabs.allergybuster.data.local.db.AllergyBusterDatabase
import com.tarnlabs.allergybuster.data.migration.RoomToSqlDelightMigrator
import com.tarnlabs.allergybuster.data.repository.RecommendationRepository
import com.tarnlabs.allergybuster.notification.NotificationHelper
import com.tarnlabs.allergybuster.worker.PollenFetchWorker
import dagger.hilt.android.HiltAndroidApp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@HiltAndroidApp
class AllergyBusterApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var recommendationRepository: RecommendationRepository
    @Inject lateinit var migrator: RoomToSqlDelightMigrator

    /** Exposed so AllergyWidget can access DB without Hilt injection (Glance limitation). */
    @Inject lateinit var database: AllergyBusterDatabase

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Block once on a fresh upgrade so workers, widget and persistent
        // notification all see migrated data. Typical cost is tens of ms.
        runBlocking { migrator.migrateIfNeeded() }
        notificationHelper.createChannel()
        scheduleDailyFetch()
        appScope.launch {
            recommendationRepository.getForDate(LocalDate.now().toString())
                ?.let { notificationHelper.postPersistentNotification(it) }
        }
    }

    private fun scheduleDailyFetch() {
        val wm = WorkManager.getInstance(this)
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val immediate = OneTimeWorkRequestBuilder<PollenFetchWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniqueWork("pollen_fetch_now", ExistingWorkPolicy.KEEP, immediate)

        val periodic = PeriodicWorkRequestBuilder<PollenFetchWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(computeInitialDelayMs(targetHour = 6), TimeUnit.MILLISECONDS)
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag("daily_pollen_fetch")
            .build()
        wm.enqueueUniquePeriodicWork("daily_pollen_fetch", ExistingPeriodicWorkPolicy.UPDATE, periodic)
    }

    private fun computeInitialDelayMs(targetHour: Int): Long {
        val now    = LocalDateTime.now()
        var target = now.withHour(targetHour).withMinute(0).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return ChronoUnit.MILLIS.between(now, target)
    }
}
