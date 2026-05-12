package com.drourke.allergybuster.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drourke.allergybuster.domain.model.Recommendation

@Composable
fun HomeScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val recommendation by viewModel.todayRecommendation.collectAsStateWithLifecycle()
    val feedback       by viewModel.todayFeedback.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        RecommendationCard(recommendation)

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
private fun RecommendationCard(recommendation: Recommendation?) {
    val targetColor = when (recommendation?.level) {
        0    -> MaterialTheme.colorScheme.primaryContainer
        1    -> MaterialTheme.colorScheme.secondaryContainer
        2    -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val cardColor by animateColorAsState(targetColor, animationSpec = tween(600), label = "card")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = cardColor),
        shape    = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier             = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment  = Alignment.CenterHorizontally
        ) {
            Text(
                text  = when (recommendation?.level) { 0 -> "✅"; 1 -> "⚠️"; 2 -> "🟠"; else -> "⏳" },
                fontSize = 48.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text       = recommendation?.advice ?: "Fetching today's forecast…",
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center
            )
            if (recommendation?.isStale == true) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Based on yesterday's data",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PollenBreakdown(recommendation: Recommendation) {
    if (recommendation.topContributors.isEmpty()) return
    Column {
        Text("Main contributors today", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            recommendation.topContributors.take(4).forEach { contributor ->
                SuggestionChip(onClick = {}, label = { Text(contributor) })
            }
        }
    }
}

@Composable
private fun FeedbackSection(alreadySubmitted: Boolean, onFeedback: (Int) -> Unit) {
    Column {
        Text(
            text  = if (alreadySubmitted) "Thanks for your feedback today" else "How are you feeling today?",
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            FeedbackButton("✅ Fine",   0, alreadySubmitted, onFeedback, Modifier.weight(1f))
            FeedbackButton("⚠️ Mild",  1, alreadySubmitted, onFeedback, Modifier.weight(1f))
            FeedbackButton("🔴 Severe", 2, alreadySubmitted, onFeedback, Modifier.weight(1f))
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
        modifier = modifier
    ) {
        Text(label, maxLines = 1)
    }
}
