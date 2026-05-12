package com.drourke.allergybuster.ui.home

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drourke.allergybuster.domain.model.Recommendation

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val recommendation   by viewModel.todayRecommendation.collectAsStateWithLifecycle()
    val feedback         by viewModel.todayFeedback.collectAsStateWithLifecycle()
    val learningProgress by viewModel.learningProgress.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        AppHeader()
        RecommendationCard(recommendation)
        LearningTreeCard(progress = learningProgress)

        if (recommendation != null) {
            PollenBreakdown(recommendation!!)
            FeedbackSection(
                alreadySubmitted = feedback != null,
                onFeedback       = viewModel::submitFeedback
            )
        }
    }
}

@Composable
private fun AppHeader() {
    Column {
        Text(
            text  = "AllergyBuster",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text  = "Cockermouth · Cumbria",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecommendationCard(recommendation: Recommendation?) {
    // Level colour mapping — green leaf / warm sand / autumn rust
    val targetColor = when (recommendation?.level) {
        0    -> MaterialTheme.colorScheme.primaryContainer    // spring leaf green
        1    -> MaterialTheme.colorScheme.secondaryContainer  // warm sandy bark
        2    -> MaterialTheme.colorScheme.errorContainer      // autumn rust/terracotta
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

    // Nature-themed icons — celebrating the outdoors, not just alerting
    val icon = when (recommendation?.level) {
        0    -> "🌿"   // thriving green sprig — you're good to go outside
        1    -> "🌾"   // golden grass ear — some pollen around, be aware
        2    -> "🌻"   // sunflower — lovely day but take your tablet!
        else -> "🌳"   // tree — loading / awaiting forecast
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = cardColor),
        shape    = RoundedCornerShape(24.dp),
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
            Text(
                text       = recommendation?.advice ?: "Fetching today's forecast…",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                color      = textColor
            )
            if (recommendation != null && recommendation.level == 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text      = "Get out and enjoy the fresh air!",
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
        }
    }
}

@Composable
private fun PollenBreakdown(recommendation: Recommendation) {
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
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recommendation.topContributors.take(4).forEach { contributor ->
                SuggestionChip(
                    onClick = {},
                    label   = { Text(contributor) },
                    colors  = SuggestionChipDefaults.suggestionChipColors(
                        containerColor    = MaterialTheme.colorScheme.primaryContainer,
                        labelColor        = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    border  = SuggestionChipDefaults.suggestionChipBorder(
                        enabled          = true,
                        borderColor      = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                )
            }
        }
    }
}

@Composable
private fun FeedbackSection(alreadySubmitted: Boolean, onFeedback: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Text(
            text  = if (alreadySubmitted) "Thanks — we'll remember that!" else "How did you feel outdoors today?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!alreadySubmitted) {
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
            FeedbackButton("🌿 Fine",   0, alreadySubmitted, onFeedback, Modifier.weight(1f))
            FeedbackButton("🌾 Mild",   1, alreadySubmitted, onFeedback, Modifier.weight(1f))
            FeedbackButton("🌻 Bad",    2, alreadySubmitted, onFeedback, Modifier.weight(1f))
        }
    }
}

@Composable
private fun FeedbackButton(
    label: String,
    severity: Int,
    disabled: Boolean,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick  = { onClick(severity) },
        enabled  = !disabled,
        modifier = modifier,
        border   = BorderStroke(
            width = 1.5.dp,
            color = if (disabled) MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        )
    ) {
        Text(label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
    }
}
