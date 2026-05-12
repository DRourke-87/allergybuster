package com.drourke.allergybuster.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.drourke.allergybuster.R
import com.drourke.allergybuster.domain.model.Recommendation
import com.drourke.allergybuster.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID      = "daily_pollen"
        const val NOTIF_ID        = 1001
        const val ACTION_FINE     = "com.drourke.allergybuster.ACTION_FINE"
        const val ACTION_MILD     = "com.drourke.allergybuster.ACTION_MILD"
        const val ACTION_SEVERE   = "com.drourke.allergybuster.ACTION_SEVERE"
        const val EXTRA_DATE      = "extra_date"
    }

    fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Daily Pollen Advice",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Morning recommendation and symptom feedback"
            enableVibration(true)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    fun postDailyNotification(recommendation: Recommendation) {
        val date = recommendation.date

        fun feedbackIntent(action: String): PendingIntent {
            val intent = Intent(context, FeedbackActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_DATE, date)
            }
            return PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val topTwo = recommendation.topContributors.take(2).joinToString(", ")
            .ifEmpty { "No significant pollen today" }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pollen)
            .setContentTitle("Today's Pollen Advice")
            .setContentText(recommendation.advice)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${recommendation.advice}\n\nMain triggers: $topTwo")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .addAction(0, "✅ Fine",    feedbackIntent(ACTION_FINE))
            .addAction(0, "⚠️ Mild",   feedbackIntent(ACTION_MILD))
            .addAction(0, "🔴 Severe", feedbackIntent(ACTION_SEVERE))
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID, notification)
    }
}
