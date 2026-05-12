package com.drourke.allergybuster

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.drourke.allergybuster.data.local.db.AllergyDatabase
import com.drourke.allergybuster.notification.NotificationHelper
import com.drourke.allergybuster.worker.PollenFetchWorker
import dagger.hilt.android.HiltAndroidApp
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class AllergyBusterApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationHelper: NotificationHelper

    /** Exposed so AllergyWidget can access DB without Hilt injection (Glance limitation). */
    @Inject lateinit var database: AllergyDatabase

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createChannel()
        scheduleDailyFetch()
    }

    private fun scheduleDailyFetch() {
        val request = PeriodicWorkRequestBuilder<PollenFetchWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(computeInitialDelayMs(targetHour = 6), TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .addTag("daily_pollen_fetch")
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily_pollen_fetch",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun computeInitialDelayMs(targetHour: Int): Long {
        val now    = LocalDateTime.now()
        var target = now.withHour(targetHour).withMinute(0).withSecond(0).withNano(0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return ChronoUnit.MILLIS.between(now, target)
    }
}
