package com.tarnlabs.allergybuster.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_weights")
data class UserWeightsEntity(
    @PrimaryKey val id: Int = 1,
    val alderWeight: Float   = 1.0f,
    val birchWeight: Float   = 1.0f,
    val grassWeight: Float   = 1.0f,
    val mugwortWeight: Float = 1.0f,
    val oliveWeight: Float   = 1.0f,
    val ragweedWeight: Float = 1.0f,
    val updatedAt: Long      = 0L
)
