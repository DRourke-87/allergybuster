package com.tarnlabs.allergybuster.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tarnlabs.allergybuster.domain.model.DailyOutlook
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun OutlookStrip(
    outlook: List<DailyOutlook>,
    onDayClick: (DailyOutlook) -> Unit,
    title: String = "Next days",
    startsToday: Boolean = false
) {
    if (outlook.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Text(
            text  = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            outlook.forEachIndexed { index, day ->
                OutlookDayChip(
                    day      = day,
                    dayLabel = outlookDayLabel(index, day, startsToday),
                    onClick  = { onDayClick(day) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun outlookDayLabel(index: Int, day: DailyOutlook, startsToday: Boolean): String {
    val tomorrowIndex = if (startsToday) 1 else 0
    return when (index) {
        tomorrowIndex - 1 -> "Today"
        tomorrowIndex     -> "Tomorrow"
        else -> runCatching {
            LocalDate.parse(day.date).format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))
        }.getOrDefault(day.date)
    }
}

@Composable
private fun OutlookDayChip(
    day: DailyOutlook,
    dayLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = when (day.level) {
        0    -> MaterialTheme.colorScheme.primaryContainer
        1    -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when (day.level) {
        0    -> MaterialTheme.colorScheme.onPrimaryContainer
        1    -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }
    val icon = when (day.level) {
        0    -> "🌿"
        1    -> "🌾"
        else -> "🌻"
    }
    val levelLabel = when (day.level) {
        0    -> "Low"
        1    -> "Moderate"
        else -> "High"
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text     = dayLabel,
            style    = MaterialTheme.typography.labelSmall,
            color    = contentColor.copy(alpha = 0.8f),
            maxLines = 1
        )
        Text(text = icon, fontSize = 22.sp)
        Text(
            text       = levelLabel,
            style      = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color      = contentColor,
            maxLines   = 1
        )
        day.topContributors.firstOrNull()?.let { top ->
            Text(
                text     = top,
                style    = MaterialTheme.typography.labelSmall,
                color    = contentColor.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}
