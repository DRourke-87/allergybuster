package com.tarnlabs.allergybuster.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarnlabs.allergybuster.data.local.datastore.AppSettingsDataStore
import com.tarnlabs.allergybuster.data.local.db.entity.DailyFeedbackEntity
import com.tarnlabs.allergybuster.data.local.db.entity.UserWeightsEntity
import com.tarnlabs.allergybuster.data.location.LocationProvider
import com.tarnlabs.allergybuster.data.repository.FeedbackRepository
import com.tarnlabs.allergybuster.data.repository.PollenRepository
import com.tarnlabs.allergybuster.data.repository.RecommendationRepository
import com.tarnlabs.allergybuster.domain.model.DailyPollen
import com.tarnlabs.allergybuster.domain.model.Recommendation
import com.tarnlabs.allergybuster.domain.usecase.SubmitFeedbackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recommendationRepository: RecommendationRepository,
    private val feedbackRepository: FeedbackRepository,
    private val pollenRepository: PollenRepository,
    private val submitFeedback: SubmitFeedbackUseCase,
    private val appSettings: AppSettingsDataStore,
    private val locationProvider: LocationProvider
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

    val recentForecasts: StateFlow<List<DailyPollen>> = pollenRepository
        .observeRecent(14)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val userWeights: StateFlow<UserWeightsEntity> = feedbackRepository
        .observeWeights()
        .map { it ?: UserWeightsEntity() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserWeightsEntity())

    val locationName: StateFlow<String> = appSettings.settingsFlow
        .map { it.locationName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    val learningProgress: StateFlow<LearningProgress> = combine(
        appSettings.learningStartedAtFlow,
        feedbackRepository.observeFeedbackCount()
    ) { startedAt, feedbackCount ->
        val effectiveStart = if (startedAt == 0L) System.currentTimeMillis() else startedAt
        val startDate = Instant.ofEpochMilli(effectiveStart).atZone(ZoneId.systemDefault()).toLocalDate()
        val rawDays = ChronoUnit.DAYS.between(startDate, LocalDate.now())
            .toInt().coerceIn(0, LEARNING_WINDOW_DAYS)
        val daysElapsed = maxOf(rawDays, feedbackCount).coerceAtMost(LEARNING_WINDOW_DAYS)
        LearningProgress.from(daysElapsed = daysElapsed, feedbackCount = feedbackCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearningProgress.INITIAL)

    fun submitFeedback(severity: Int) {
        viewModelScope.launch {
            submitFeedback(today, severity)
            // Backfill location if it wasn't resolved when the recommendation was first written.
            val rec = todayRecommendation.value
            if (rec != null && rec.locationName.isEmpty()) {
                locationProvider.getLocation()?.let { loc ->
                    appSettings.setLocation(loc.lat, loc.lon, loc.name)
                    recommendationRepository.save(rec.copy(locationName = loc.name))
                }
            }
        }
    }

    companion object {
        const val LEARNING_WINDOW_DAYS = 30
    }
}
