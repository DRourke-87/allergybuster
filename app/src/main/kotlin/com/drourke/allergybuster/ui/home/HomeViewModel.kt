package com.drourke.allergybuster.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drourke.allergybuster.data.local.datastore.AppSettingsDataStore
import com.drourke.allergybuster.data.local.db.entity.DailyFeedbackEntity
import com.drourke.allergybuster.data.repository.FeedbackRepository
import com.drourke.allergybuster.data.repository.RecommendationRepository
import com.drourke.allergybuster.domain.model.Recommendation
import com.drourke.allergybuster.domain.usecase.SubmitFeedbackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.min

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recommendationRepository: RecommendationRepository,
    private val feedbackRepository: FeedbackRepository,
    private val submitFeedback: SubmitFeedbackUseCase,
    private val appSettings: AppSettingsDataStore
) : ViewModel() {

    private val today = LocalDate.now().toString()

    init {
        viewModelScope.launch { appSettings.ensureLearningStarted() }
    }

    val todayRecommendation: StateFlow<Recommendation?> = recommendationRepository
        .observeRecent(1)
        .map { list -> list.find { it.date == today } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val todayFeedback: StateFlow<DailyFeedbackEntity?> = feedbackRepository
        .observeRecentFeedback(1)
        .map { list -> list.find { it.date == today } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val learningProgress: StateFlow<LearningProgress> = combine(
        appSettings.learningStartedAtFlow,
        feedbackRepository.observeFeedbackCount()
    ) { startedAt, feedbackCount ->
        val effectiveStart = if (startedAt == 0L) System.currentTimeMillis() else startedAt
        val rawDays = min(
            LEARNING_WINDOW_DAYS.toLong(),
            (System.currentTimeMillis() - effectiveStart) / MILLIS_PER_DAY
        ).toInt().coerceAtLeast(0)
        val daysElapsed = maxOf(rawDays, feedbackCount).coerceAtMost(LEARNING_WINDOW_DAYS)
        LearningProgress.from(daysElapsed = daysElapsed, feedbackCount = feedbackCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearningProgress.INITIAL)

    fun submitFeedback(severity: Int) {
        viewModelScope.launch { submitFeedback(today, severity) }
    }

    companion object {
        const val LEARNING_WINDOW_DAYS = 30
        private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L
    }
}
