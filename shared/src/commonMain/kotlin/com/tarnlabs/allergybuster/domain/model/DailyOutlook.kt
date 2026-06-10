package com.tarnlabs.allergybuster.domain.model

/**
 * Risk outlook for a single forecast day, computed live from cached pollen data
 * and the user's current weights. Never persisted — unlike [Recommendation],
 * which records what the user was actually told.
 */
data class DailyOutlook(
    val date: String,           // "YYYY-MM-DD"
    val level: Int,
    val score: Float,
    val topContributors: List<String>,
    val pollen: DailyPollen
)
