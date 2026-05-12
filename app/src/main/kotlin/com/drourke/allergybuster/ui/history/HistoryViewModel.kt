package com.drourke.allergybuster.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drourke.allergybuster.data.local.db.entity.DailyFeedbackEntity
import com.drourke.allergybuster.data.repository.FeedbackRepository
import com.drourke.allergybuster.data.repository.RecommendationRepository
import com.drourke.allergybuster.domain.model.Recommendation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HistoryDay(
    val recommendation: Recommendation,
    val feedback: DailyFeedbackEntity?
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    recommendationRepository: RecommendationRepository,
    feedbackRepository: FeedbackRepository
) : ViewModel() {

    val history: StateFlow<List<HistoryDay>> = combine(
        recommendationRepository.observeRecent(90),
        feedbackRepository.observeRecentFeedback(90)
    ) { recs, feedbacks ->
        val feedbackMap = feedbacks.associateBy { it.date }
        recs.map { rec -> HistoryDay(rec, feedbackMap[rec.date]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
