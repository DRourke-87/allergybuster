package com.drourke.allergybuster

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
        val wm = WorkManager.getInstance(this)
        val networkConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Fetch immediately on each launch so the home screen is never empty.
        // KEEP means we won't duplicate if a fetch is already queued or running.
        val immediate = OneTimeWorkRequestBuilder<PollenFetchWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniqueWork("pollen_fetch_now", ExistingWorkPolicy.KEEP, immediate)

        // Also schedule the daily 06:00 recurring fetch for background updates.
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
