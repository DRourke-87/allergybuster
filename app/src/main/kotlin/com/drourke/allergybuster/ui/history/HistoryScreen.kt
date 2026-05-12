package com.drourke.allergybuster.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val history by viewModel.history.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Text(
                text  = "Your History",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text  = "Forecast vs how you actually felt",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (history.isEmpty()) {
            Column(
                modifier            = Modifier.fillMaxSize().padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🌳", fontSize = 48.sp)
                Text(
                    "Nothing here yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Once the app fetches its first forecast, your daily history will appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return
        }

        LazyColumn(
            contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(history, key = { it.recommendation.date }) { day ->
                HistoryDayRow(day)
            }
        }
    }
}

@Composable
private fun HistoryDayRow(day: HistoryDay) {
    val levelIcon = when (day.recommendation.level) {
        0 -> "🌿"; 1 -> "🌾"; else -> "🌻"
    }
    val levelLabel = when (day.recommendation.level) {
        0 -> "Clear"; 1 -> "Moderate"; else -> "High"
    }
    val levelContainerColor = when (day.recommendation.level) {
        0    -> MaterialTheme.colorScheme.primaryContainer
        1    -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val levelContentColor = when (day.recommendation.level) {
        0    -> MaterialTheme.colorScheme.onPrimaryContainer
        1    -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }

    val feedbackLabel = when (day.feedback?.severity) {
        0 -> "🌿 Fine"; 1 -> "🌾 Mild"; 2 -> "🌻 Bad"; else -> null
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier              = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(levelIcon, fontSize = 24.sp)
                Column {
                    Text(
                        day.recommendation.date,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        day.recommendation.advice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "📍 ${day.recommendation.locationName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SuggestionChip(
                    onClick = {},
                    label   = { Text(levelLabel, style = MaterialTheme.typography.labelSmall) },
                    colors  = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = levelContainerColor,
                        labelColor     = levelContentColor
                    ),
                    border = SuggestionChipDefaults.suggestionChipBorder(enabled = false)
                )
                feedbackLabel?.let { label ->
                    SuggestionChip(
                        onClick = {},
                        label   = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        colors  = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor     = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        border = SuggestionChipDefaults.suggestionChipBorder(enabled = false)
                    )
                }
            }
        }
    }
}
