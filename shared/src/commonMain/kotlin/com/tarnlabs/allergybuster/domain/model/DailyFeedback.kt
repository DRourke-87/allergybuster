package com.tarnlabs.allergybuster.domain.model

data class DailyFeedback(
    val date: String,
    val severity: Int,           // 0=fine, 1=mild, 2=severe
    val recordedAt: Long,
    val bayesianApplied: Boolean = false
)
