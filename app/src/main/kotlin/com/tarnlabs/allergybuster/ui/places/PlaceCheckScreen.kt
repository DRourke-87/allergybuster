package com.tarnlabs.allergybuster.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tarnlabs.allergybuster.domain.engine.RecommendationEngine
import com.tarnlabs.allergybuster.domain.model.DailyOutlook
import com.tarnlabs.allergybuster.ui.home.OutlookStrip
import com.tarnlabs.allergybuster.ui.home.levelColour

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceCheckScreen(
    onBack: () -> Unit,
    viewModel: PlaceCheckViewModel = hiltViewModel()
) {
    val query         by viewModel.query.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val outlookState  by viewModel.outlookState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title          = { Text("Check another location") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value         = query,
                onValueChange = viewModel::onQueryChange,
                modifier      = Modifier.fillMaxWidth(),
                placeholder   = { Text("Search a city or town…") },
                leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine    = true
            )

            when (val state = outlookState) {
                is PlaceCheckViewModel.OutlookState.Idle ->
                    SearchResultsList(
                        results  = searchResults,
                        onSelect = viewModel::selectPlace
                    )
                is PlaceCheckViewModel.OutlookState.Loading ->
                    LoadingState(state.place.displayName)
                is PlaceCheckViewModel.OutlookState.Error ->
                    ErrorState(state.place.displayName, onRetry = viewModel::retry)
                is PlaceCheckViewModel.OutlookState.Loaded ->
                    PlaceOutlook(placeName = state.place.displayName, outlook = state.outlook)
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<com.tarnlabs.allergybuster.domain.model.PlaceResult>,
    onSelect: (com.tarnlabs.allergybuster.domain.model.PlaceResult) -> Unit
) {
    if (results.isEmpty()) {
        Text(
            text  = "Planning a trip? Search a place to see its pollen outlook, scored with your own sensitivity profile.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(results, key = { "${it.latitude},${it.longitude}" }) { place ->
            ListItem(
                headlineContent   = { Text(place.name) },
                supportingContent = {
                    val detail = listOf(place.region, place.country)
                        .filter { it.isNotEmpty() }
                        .joinToString(", ")
                    if (detail.isNotEmpty()) Text(detail)
                },
                leadingContent = { Text("📍") },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(place) }
            )
        }
    }
}

@Composable
private fun LoadingState(placeName: String) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text  = "Fetching forecast for $placeName…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorState(placeName: String, onRetry: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxWidth().padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text  = "Couldn't load the forecast for $placeName. Check your connection and try again.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onRetry, shape = RoundedCornerShape(8.dp)) {
            Text("Retry")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceOutlook(placeName: String, outlook: List<DailyOutlook>) {
    var selectedDate by rememberSaveable(placeName) {
        mutableStateOf(outlook.firstOrNull()?.date)
    }
    val selectedDay = outlook.find { it.date == selectedDate } ?: outlook.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text  = "📍 $placeName",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        OutlookStrip(
            outlook     = outlook,
            onDayClick  = { selectedDate = it.date },
            title       = "Pollen outlook (local days)"
        )
        selectedDay?.let { DayBreakdown(it) }
        Text(
            text  = "Based on your sensitivity profile — pollen information only, not medical advice.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayBreakdown(day: DailyOutlook) {
    val active = RecommendationEngine.activePollenLevels(day.pollen)
    if (active.isEmpty()) {
        Text(
            text  = "No significant pollen on ${day.date}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Text(
            text  = "Active pollen on ${day.date}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp)
        ) {
            active.forEach { entry ->
                val colour = levelColour(entry.norm)
                val levelLabel = when (entry.level) {
                    0    -> "Low"
                    1    -> "Moderate"
                    else -> "High"
                }
                SuggestionChip(
                    onClick = {},
                    label   = { Text("${entry.type.icon} ${entry.type.displayName} · $levelLabel") },
                    colors  = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = colour.copy(alpha = 0.18f),
                        labelColor     = MaterialTheme.colorScheme.onSurface
                    ),
                    border  = SuggestionChipDefaults.suggestionChipBorder(
                        enabled     = true,
                        borderColor = colour.copy(alpha = 0.6f)
                    )
                )
            }
        }
    }
}
