package com.tarnlabs.allergybuster.domain.model

data class Recommendation(
    val date: String,
    val level: Int,                  // 0=none, 1=consider, 2=take
    val score: Float,
    val advice: String,
    val topContributors: List<String>,
    val computedAt: Long,
    val isStale: Boolean = false,    // true if based on yesterday's forecast
    val locationName: String = ""
)
