package com.tarnlabs.allergybuster.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tarnlabs.allergybuster.domain.model.DailyOutlook
import com.tarnlabs.allergybuster.domain.model.PlaceResult
import com.tarnlabs.allergybuster.domain.usecase.CheckLocationOutlookUseCase
import com.tarnlabs.allergybuster.domain.usecase.SearchPlacesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaceCheckViewModel @Inject constructor(
    private val searchPlaces: SearchPlacesUseCase,
    private val checkLocationOutlook: CheckLocationOutlookUseCase
) : ViewModel() {

    sealed interface OutlookState {
        data object Idle : OutlookState
        data class Loading(val place: PlaceResult) : OutlookState
        data class Loaded(val place: PlaceResult, val outlook: List<DailyOutlook>) : OutlookState
        data class Error(val place: PlaceResult) : OutlookState
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _outlookState = MutableStateFlow<OutlookState>(OutlookState.Idle)
    val outlookState: StateFlow<OutlookState> = _outlookState.asStateFlow()

    val searchResults: StateFlow<List<PlaceResult>> = _query
        .debounce(400)
        .mapLatest { q ->
            if (q.trim().length < 2) emptyList()
            else runCatching { searchPlaces(q) }.getOrDefault(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) {
        _query.value = value
        _outlookState.value = OutlookState.Idle
    }

    fun selectPlace(place: PlaceResult) {
        _outlookState.value = OutlookState.Loading(place)
        viewModelScope.launch {
            _outlookState.value = runCatching { checkLocationOutlook(place.latitude, place.longitude) }
                .map { OutlookState.Loaded(place, it) }
                .getOrDefault(OutlookState.Error(place))
        }
    }

    fun retry() {
        when (val state = _outlookState.value) {
            is OutlookState.Error -> selectPlace(state.place)
            else -> Unit
        }
    }
}
