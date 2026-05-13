package com.tarnlabs.allergybuster.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tarnlabs.allergybuster.R
import com.tarnlabs.allergybuster.domain.model.Recommendation
import com.tarnlabs.allergybuster.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_ID            = "daily_pollen"
        const val CHANNEL_PERSISTENT_ID = "pollen_status"
        const val NOTIF_ID              = 1001
        const val PERSISTENT_NOTIF_ID   = 1002
        const val LOCATION_NOTIF_ID     = 1003
        const val ACTION_FINE           = "com.tarnlabs.allergybuster.ACTION_FINE"
        const val ACTION_MILD           = "com.tarnlabs.allergybuster.ACTION_MILD"
        const val ACTION_SEVERE         = "com.tarnlabs.allergybuster.ACTION_SEVERE"
        const val EXTRA_DATE            = "extra_date"
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun createChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)

        // Morning alert — default importance (sound + vibration)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Daily Pollen Advice", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Morning recommendation and symptom feedback"
                enableVibration(true)
            }
        )

        // Persistent status — low importance (silent, no vibration, stays in shade)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PERSISTENT_ID, "Pollen Status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live pollen level shown throughout the day"
                setShowBadge(false)
            }
        )
    }

    /** Dismissable morning alert with Fine / Mild / Severe feedback actions. */
    @SuppressLint("MissingPermission") // guarded by canPostNotifications()
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

        val topTwo = recommendation.topContributors.take(2).joinToString(", ")
            .ifEmpty { "No significant pollen today" }

        if (!canPostNotifications()) return
        NotificationManagerCompat.from(context).notify(
            NOTIF_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pollen)
                .setContentTitle("Today's Pollen Advice")
                .setContentText(recommendation.advice)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("${recommendation.advice}\n\nMain triggers: $topTwo")
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .addAction(0, "✅ Fine",    feedbackIntent(ACTION_FINE))
                .addAction(0, "⚠️ Mild",   feedbackIntent(ACTION_MILD))
                .addAction(0, "🔴 Severe", feedbackIntent(ACTION_SEVERE))
                .build()
        )
    }

    /** Ongoing silent notification — stays in the shade and updates with each fresh fetch. */
    @SuppressLint("MissingPermission") // guarded by canPostNotifications()
    fun postPersistentNotification(recommendation: Recommendation) {
        val emoji = when (recommendation.level) {
            0 -> "🌿"; 1 -> "🌾"; 2 -> "🌻"; else -> "🌳"
        }
        val topTwo = recommendation.topContributors.take(2).joinToString(" · ")
            .ifEmpty { "No significant pollen" }

        if (!canPostNotifications()) return
        NotificationManagerCompat.from(context).notify(
            PERSISTENT_NOTIF_ID,
            NotificationCompat.Builder(context, CHANNEL_PERSISTENT_ID)
                .setSmallIcon(R.drawable.ic_pollen)
                .setContentTitle("$emoji  ${recommendation.advice}")
                .setContentText("${recommendation.locationName} · $topTwo")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)
                .setContentIntent(openAppIntent())
                .build()
        )
    }

    /**
     * Shown when the worker detects the user has moved >10 km from their last known location.
     * Prompts them to re-rate how they feel in the new location.
     */
    @SuppressLint("MissingPermission") // guarded by canPostNotifications()
    fun postLocationChangedNotification(newLocationName: String, date: String) {
        fun feedbackIntent(action: String): PendingIntent {
            val intent = Intent(context, FeedbackActionReceiver::class.java).apply {
                this.action = action
                putExtra(EXTRA_DATE, date)
            }
            return PendingIntent.getBroadcast(
                context,
                // Use offset to avoid colliding with daily notification request codes
                (action + "_loc").hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        if (!canPostNotifications()) return
        NotificationManagerCompat.from(context).notify(
            LOCATION_NOTIF_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_pollen)
                .setContentTitle("📍 You've moved to $newLocationName")
                .setContentText("How are your symptoms feeling today?")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openAppIntent())
                .addAction(0, "✅ Fine",    feedbackIntent(ACTION_FINE))
                .addAction(0, "⚠️ Mild",   feedbackIntent(ACTION_MILD))
                .addAction(0, "🔴 Severe", feedbackIntent(ACTION_SEVERE))
                .build()
        )
    }

    fun cancelPersistentNotification() {
        NotificationManagerCompat.from(context).cancel(PERSISTENT_NOTIF_ID)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
