package com.tarnlabs.allergybuster.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.tarnlabs.allergybuster.AllergyBusterApp
import com.tarnlabs.allergybuster.data.local.db.entity.RecommendationEntity
import com.tarnlabs.allergybuster.ui.MainActivity
import kotlinx.serialization.json.Json
import java.time.LocalDate

class AllergyWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db    = (context.applicationContext as AllergyBusterApp).database
        val today = LocalDate.now().toString()
        val rec   = db.recommendationDao().getForDate(today)

        provideContent { WidgetContent(rec) }
    }
}

@Composable
private fun WidgetContent(rec: RecommendationEntity?) {
    val bgColor = when (rec?.level) {
        0    -> GlanceTheme.colors.primaryContainer
        1    -> GlanceTheme.colors.secondaryContainer
        2    -> GlanceTheme.colors.errorContainer
        else -> GlanceTheme.colors.surface
    }
    val emoji = when (rec?.level) { 0 -> "✅"; 1 -> "⚠️"; 2 -> "🟠"; else -> "⏳" }
    val short = when (rec?.level) {
        0    -> "Low pollen"
        1    -> "Moderate pollen"
        2    -> "High pollen"
        else -> "Fetching…"
    }

    val contributors: List<String> = try {
        rec?.topContributors?.let { Json.decodeFromString(it) } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = TextStyle(fontSize = 28.sp))
            Text(short, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold))
            contributors.take(2).forEach { name ->
                Text(name, style = TextStyle(fontSize = 11.sp))
            }
        }
    }
}
