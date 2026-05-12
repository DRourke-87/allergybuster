package com.drourke.allergybuster.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.drourke.allergybuster.worker.FeedbackSubmitWorker
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate

@AndroidEntryPoint
class FeedbackActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val severity = when (intent.action) {
            NotificationHelper.ACTION_FINE   -> 0
            NotificationHelper.ACTION_MILD   -> 1
            NotificationHelper.ACTION_SEVERE -> 2
            else -> return
        }
        val date = intent.getStringExtra(NotificationHelper.EXTRA_DATE)
            ?: LocalDate.now().toString()

        val work = OneTimeWorkRequestBuilder<FeedbackSubmitWorker>()
            .setInputData(workDataOf("date" to date, "severity" to severity))
            .build()
        WorkManager.getInstance(context).enqueue(work)

        NotificationManagerCompat.from(context).cancel(NotificationHelper.NOTIF_ID)
    }
}
