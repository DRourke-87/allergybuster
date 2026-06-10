package com.tarnlabs.allergybuster.domain.usecase

import com.tarnlabs.allergybuster.data.repository.FeedbackRepository
import com.tarnlabs.allergybuster.data.repository.PollenRepository
import com.tarnlabs.allergybuster.domain.engine.RecommendationEngine
import com.tarnlabs.allergybuster.domain.model.DailyOutlook
import com.tarnlabs.allergybuster.domain.model.UserWeights
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Streams the risk outlook for the days after today, scored live against the
 * user's current weights. Days whose data is older than [MAX_AGE_MS] are
 * dropped — a stale multi-day forecast is worse than none.
 */
class ObserveOutlookUseCase(
    private val pollenRepository: PollenRepository,
    private val feedbackRepository: FeedbackRepository
) {
    operator fun invoke(): Flow<List<DailyOutlook>> {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault()).toString()
        return combine(
            pollenRepository.observeFromDate(today),
            feedbackRepository.observeWeights()
        ) { days, weights ->
            val now = Clock.System.now().toEpochMilliseconds()
            days.filter { it.date > today && now - it.fetchedAt <= MAX_AGE_MS }
                .map { RecommendationEngine.computeOutlook(it, weights ?: UserWeights()) }
        }.catch { emit(emptyList()) }
    }

    companion object {
        const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
