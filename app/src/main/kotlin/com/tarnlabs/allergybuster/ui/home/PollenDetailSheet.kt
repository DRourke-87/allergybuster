package com.tarnlabs.allergybuster.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tarnlabs.allergybuster.data.local.db.entity.UserWeightsEntity
import com.tarnlabs.allergybuster.domain.model.DailyPollen
import com.tarnlabs.allergybuster.domain.model.PollenType
import com.tarnlabs.allergybuster.domain.model.getWeight
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PollenDetailSheet(
    pollenType: PollenType,
    recentForecasts: List<DailyPollen>,
    userWeights: UserWeightsEntity,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pollenType.icon, fontSize = 40.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        pollenType.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Pollen details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val today = LocalDate.now().toString()
            val todayPollen = recentForecasts.find { it.date == today }
            val todayNorm = todayPollen?.let { pollenType.normalise(it.getRaw(pollenType)) } ?: 0f
            val todayRaw = todayPollen?.getRaw(pollenType) ?: 0f
            TodayLevelCard(normValue = todayNorm, rawValue = todayRaw)

            val sortedForecasts = recentForecasts.sortedBy { it.date }
            if (sortedForecasts.size >= 2) {
                TrendCard(pollenType = pollenType, forecasts = sortedForecasts, today = today)
            }

            InfoCard(icon = "📅", label = "Season", body = pollenType.seasonality)
            InfoCard(icon = "⚠️", label = "Cross-reactions", body = pollenType.crossReactions)
            SensitivityCard(weight = userWeights.getWeight(pollenType))
        }
    }
}

@Composable
private fun TodayLevelCard(normValue: Float, rawValue: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Today",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${rawValue.toInt()} grains/m³",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        Spacer(Modifier.height(10.dp))
        LevelProgressBar(normValue)
        Spacer(Modifier.height(6.dp))
        val levelLabel = when {
            normValue < 1f -> "Low"
            normValue < 2f -> "Moderate"
            else           -> "High"
        }
        Text(
            levelLabel,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = levelColour(normValue)
        )
    }
}

@Composable
private fun LevelProgressBar(normValue: Float) {
    val fraction = (normValue / 3f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .clip(RoundedCornerShape(4.dp))
                .background(levelColour(normValue))
        )
    }
}

@Composable
private fun TrendCard(pollenType: PollenType, forecasts: List<DailyPollen>, today: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Text(
            "Recent & forecast",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        TrendBars(pollenType = pollenType, forecasts = forecasts, today = today)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LegendDot(colour = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), label = "Past")
            LegendDot(colour = levelColour(1.5f), label = "Today")
            LegendDot(colour = levelColour(1.5f).copy(alpha = 0.5f), label = "Forecast")
        }
    }
}

@Composable
private fun TrendBars(pollenType: PollenType, forecasts: List<DailyPollen>, today: String) {
    val barHeight = 80.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        forecasts.forEach { pollen ->
            val normValue = pollenType.normalise(pollen.getRaw(pollenType))
            val fraction = (normValue / 3f).coerceIn(0.03f, 1f)
            val isToday = pollen.date == today
            val isFuture = pollen.date > today
            val barColour = when {
                isToday  -> levelColour(normValue)
                isFuture -> levelColour(normValue).copy(alpha = 0.55f)
                else     -> MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            }
            val dayLabel = try {
                LocalDate.parse(pollen.date)
                    .dayOfWeek
                    .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    .take(3)
            } catch (_: Exception) { "" }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier.height(barHeight),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(barColour)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun LegendDot(colour: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colour)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun InfoCard(icon: String, label: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(icon, fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SensitivityCard(weight: Float) {
    val description = when {
        weight < 0.7f -> "You appear less reactive to this pollen than average — it contributes less to your personalised risk score."
        weight > 1.4f -> "You appear more sensitive to this pollen than average — it carries extra weight in your personalised forecast."
        else          -> "Your sensitivity to this pollen is close to average."
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text("🎯", fontSize = 20.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                "Your sensitivity",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            val fraction = ((weight - 0.1f) / (5.0f - 0.1f)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Sensitivity index: ${"%.2f".format(weight)}× (default 1.00×)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun levelColour(normValue: Float): Color = when {
    normValue < 1f -> Color(0xFF4CAF50)
    normValue < 2f -> Color(0xFFFF9800)
    else           -> Color(0xFFF44336)
}
