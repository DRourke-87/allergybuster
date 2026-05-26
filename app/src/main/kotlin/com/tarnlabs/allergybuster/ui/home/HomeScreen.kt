package com.tarnlabs.allergybuster.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tarnlabs.allergybuster.domain.model.PollenType
import com.tarnlabs.allergybuster.domain.model.Recommendation
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val recommendation   by viewModel.todayRecommendation.collectAsStateWithLifecycle()
    val feedback         by viewModel.todayFeedback.collectAsStateWithLifecycle()
    val learningProgress by viewModel.learningProgress.collectAsStateWithLifecycle()
    val locationName     by viewModel.locationName.collectAsStateWithLifecycle()
    val recentForecasts  by viewModel.recentForecasts.collectAsStateWithLifecycle()
    val userWeights      by viewModel.userWeights.collectAsStateWithLifecycle()
    val isRetrying       by viewModel.isRetrying.collectAsStateWithLifecycle()

    var showRetry by remember { mutableStateOf(false) }
    LaunchedEffect(recommendation) {
        showRetry = false
        if (recommendation == null) {
            delay(30_000)
            showRetry = true
        }
    }

    var selectedPollenType by rememberSaveable { mutableStateOf<String?>(null) }

    if (selectedPollenType != null) {
        val pollenType = PollenType.fromDisplayName(selectedPollenType!!)
        if (pollenType != null) {
            PollenDetailSheet(
                pollenType      = pollenType,
                recentForecasts = recentForecasts,
                userWeights     = userWeights,
                onDismiss       = { selectedPollenType = null }
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AppHeader(locationName)
        RecommendationCard(
            recommendation = recommendation,
            showRetry      = showRetry,
            isRetrying     = isRetrying,
            onRetry        = viewModel::retryForecastFetch
        )

        if (recommendation != null) {
            PollenBreakdown(
                recommendation   = recommendation!!,
                onPollenChipClick = { name -> selectedPollenType = name }
            )
            FeedbackSection(
                selectedSeverity = feedback?.severity,
                recordedAt       = feedback?.recordedAt,
                onFeedback       = viewModel::submitFeedback
            )
        }

        LearningTreeCard(progress = learningProgress)
    }
}

@Composable
private fun AppHeader(locationName: String) {
    Column {
        Text(
            text  = "AllergyBuster",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (locationName.isNotEmpty()) {
            Text(
                text  = locationName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: Recommendation?,
    showRetry: Boolean = false,
    isRetrying: Boolean = false,
    onRetry: () -> Unit = {}
) {
    val targetColor = when (recommendation?.level) {
        0    -> MaterialTheme.colorScheme.primaryContainer
        1    -> MaterialTheme.colorScheme.secondaryContainer
        2    -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onTargetColor = when (recommendation?.level) {
        0    -> MaterialTheme.colorScheme.onPrimaryContainer
        1    -> MaterialTheme.colorScheme.onSecondaryContainer
        2    -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val cardColor by animateColorAsState(targetColor,   animationSpec = tween(700), label = "card-bg")
    val textColor by animateColorAsState(onTargetColor, animationSpec = tween(700), label = "card-text")

    val icon = when (recommendation?.level) {
        0    -> "🌿"
        1    -> "🌾"
        2    -> "🌻"
        else -> "🌳"
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = cardColor),
        shape     = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier            = Modifier
                .padding(28.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = icon, fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            val loadingText = if (showRetry) {
                "Still fetching — tap retry if this seems stuck."
            } else {
                "Fetching today's forecast…"
            }
            Text(
                text       = recommendation?.advice ?: loadingText,
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                color      = textColor
            )
            if (recommendation == null && showRetry) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onRetry,
                    enabled = !isRetrying,
                    shape   = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isRetrying) "Retrying…" else "Retry")
                }
            }
            val subtitle = when (recommendation?.level) {
                0 -> "Get out and enjoy the fresh air!"
                1 -> "Hay fever sufferers may wish to take precautions"
                2 -> "High risk for hay fever sufferers today"
                else -> null
            }
            if (recommendation != null && subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text      = subtitle,
                    style     = MaterialTheme.typography.bodyMedium,
                    color     = textColor.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
            if (recommendation?.isStale == true) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text  = "Based on yesterday's data",
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor.copy(alpha = 0.65f)
                )
            }
            if (recommendation != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text      = "Pollen information only — not medical advice",
                    style     = MaterialTheme.typography.bodySmall,
                    color     = textColor.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PollenBreakdown(recommendation: Recommendation, onPollenChipClick: (String) -> Unit) {
    if (recommendation.topContributors.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Text(
            text  = "Today's main pollen sources",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text  = "Tap any source for details, trends and cross-reactions",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recommendation.topContributors.take(4).forEach { contributor ->
                SuggestionChip(
                    onClick = { onPollenChipClick(contributor) },
                    label   = { Text(contributor) },
                    colors  = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        labelColor     = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border  = SuggestionChipDefaults.suggestionChipBorder(
                        enabled     = true,
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

@Composable
private fun FeedbackSection(
    selectedSeverity: Int?,
    recordedAt: Long?,
    onFeedback: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Text(
            text  = "How are you feeling today?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (selectedSeverity != null && recordedAt != null) {
            val timeStr = Instant.ofEpochMilli(recordedAt)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            Text(
                text  = "Last updated at $timeStr — tap again to change",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        } else {
            Text(
                text  = "Your feedback helps personalise your pollen sensitivity.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FeedbackButton("🌿 Fine", 0, selectedSeverity, onFeedback, Modifier.weight(1f))
            FeedbackButton("🌾 Mild", 1, selectedSeverity, onFeedback, Modifier.weight(1f))
            FeedbackButton("🌻 Bad",  2, selectedSeverity, onFeedback, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FeedbackButton(
    label: String,
    severity: Int,
    selectedSeverity: Int?,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = selectedSeverity == severity
    if (isSelected) {
        Button(
            onClick  = { onClick(severity) },
            modifier = modifier,
            shape    = RoundedCornerShape(8.dp)
        ) {
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
    } else {
        OutlinedButton(
            onClick  = { onClick(severity) },
            modifier = modifier,
            shape    = RoundedCornerShape(8.dp),
            border   = BorderStroke(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            )
        ) {
            Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
        }
    }
}
