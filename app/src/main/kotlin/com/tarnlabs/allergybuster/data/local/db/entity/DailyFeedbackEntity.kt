package com.tarnlabs.allergybuster.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_feedback")
data class DailyFeedbackEntity(
    @PrimaryKey val date: String,    // "YYYY-MM-DD"
    val severity: Int,               // 0=fine, 1=mild, 2=severe
    val recordedAt: Long
)
