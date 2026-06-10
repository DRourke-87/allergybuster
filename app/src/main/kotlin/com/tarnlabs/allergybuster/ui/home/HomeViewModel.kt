package com.tarnlabs.allergybuster.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import com.tarnlabs.allergybuster.data.local.datastore.AppSettingsDataStore
import com.tarnlabs.allergybuster.data.location.LocationProvider
import com.tarnlabs.allergybuster.data.repository.FeedbackRepository
import com.tarnlabs.allergybuster.data.repository.PollenRepository
import com.tarnlabs.allergybuster.data.repository.RecommendationRepository
import com.tarnlabs.allergybuster.domain.model.DailyFeedback
import com.tarnlabs.allergybuster.domain.model.DailyOutlook
import com.tarnlabs.allergybuster.domain.model.DailyPollen
import com.tarnlabs.allergybuster.domain.model.Recommendation
import com.tarnlabs.allergybuster.domain.model.UserWeights
import com.tarnlabs.allergybuster.domain.usecase.ObserveOutlookUseCase
import com.tarnlabs.allergybuster.domain.usecase.SubmitFeedbackUseCase
import com.tarnlabs.allergybuster.enqueueImmediatePollenFetch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    observeOutlook: ObserveOutlookUseCase,
    private val appSettings: AppSettingsDataStore,
    private val locationProvider: LocationProvider,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val today = LocalDate.now().toString()

    init {
        viewModelScope.launch { appSettings.ensureLearningStarted() }
    }

    val todayRecommendation: StateFlow<Recommendation?> = recommendationRepository
        .observeRecent(1)
        .map { list -> list.find { it.date == today } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val todayFeedback: StateFlow<DailyFeedback?> = feedbackRepository
        .observeRecentFeedback(1)
        .map { list -> list.find { it.date == today } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val recentForecasts: StateFlow<List<DailyPollen>> = pollenRepository
        .observeRecent(14)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val outlook: StateFlow<List<DailyOutlook>> = observeOutlook()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val userWeights: StateFlow<UserWeights> = feedbackRepository
        .observeWeights()
        .map { it ?: UserWeights() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserWeights())

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
        val daysElapsed = maxOf(rawDays, feedbackCount.toInt()).coerceAtMost(LEARNING_WINDOW_DAYS)
        LearningProgress.from(daysElapsed = daysElapsed, feedbackCount = feedbackCount.toInt())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LearningProgress.INITIAL)

    private val _isRetrying = MutableStateFlow(false)
    val isRetrying: StateFlow<Boolean> = _isRetrying.asStateFlow()

    fun retryForecastFetch() {
        if (_isRetrying.value) return
        _isRetrying.value = true
        viewModelScope.launch {
            enqueueImmediatePollenFetch(context, ExistingWorkPolicy.REPLACE)
            delay(2_000)
            _isRetrying.value = false
        }
    }

    fun submitFeedback(severity: Int) {
        viewModelScope.launch {
            submitFeedback(today, severity)
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
