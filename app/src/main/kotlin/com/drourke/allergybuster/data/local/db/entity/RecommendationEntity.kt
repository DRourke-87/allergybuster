package com.drourke.allergybuster.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recommendation")
data class RecommendationEntity(
    @PrimaryKey val date: String,
    val level: Int,
    val score: Float,
    val advice: String,
    val topContributors: String,     // JSON-encoded List<String> via TypeConverter
    val computedAt: Long,
    val isStale: Boolean = false
)
