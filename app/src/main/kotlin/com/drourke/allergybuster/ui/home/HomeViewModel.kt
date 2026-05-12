package com.drourke.allergybuster.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drourke.allergybuster.data.local.db.entity.DailyFeedbackEntity
import com.drourke.allergybuster.data.repository.FeedbackRepository
import com.drourke.allergybuster.data.repository.RecommendationRepository
import com.drourke.allergybuster.domain.model.Recommendation
import com.drourke.allergybuster.domain.usecase.SubmitFeedbackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recommendationRepository: RecommendationRepository,
    private val feedbackRepository: FeedbackRepository,
    private val submitFeedback: SubmitFeedbackUseCase
) : ViewModel() {

    private val today = LocalDate.now().toString()

    val todayRecommendation: StateFlow<Recommendation?> = recommendationRepository
        .observeRecent(1)
        .map { list -> list.find { it.date == today } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val todayFeedback: StateFlow<DailyFeedbackEntity?> = feedbackRepository
        .observeRecentFeedback(1)
        .map { list -> list.find { it.date == today } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun submitFeedback(severity: Int) {
        viewModelScope.launch { submitFeedback(today, severity) }
    }
}
