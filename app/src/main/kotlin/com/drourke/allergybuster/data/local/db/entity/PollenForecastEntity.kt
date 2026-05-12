package com.drourke.allergybuster.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pollen_forecast")
data class PollenForecastEntity(
    @PrimaryKey val date: String,    // "YYYY-MM-DD"
    val alderMax: Float,
    val birchMax: Float,
    val grassMax: Float,
    val mugwortMax: Float,
    val oliveMax: Float,
    val ragweedMax: Float,
    val fetchedAt: Long
)
