package com.tarnlabs.allergybuster.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tarnlabs.allergybuster.ui.theme.BarkBrown
import com.tarnlabs.allergybuster.ui.theme.ForestGreen
import com.tarnlabs.allergybuster.ui.theme.SpringLeafContainer
import com.tarnlabs.allergybuster.ui.theme.SunlightGold
import kotlin.math.min

data class LearningProgress(
    val daysElapsed: Int,
    val feedbackCount: Int,
    val progressFraction: Float
) {
    val isMature: Boolean get() = progressFraction >= 1f

    companion object {
        val INITIAL = LearningProgress(0, 0, 0f)

        fun from(daysElapsed: Int, feedbackCount: Int, windowDays: Int = 30): LearningProgress {
            val dayFrac = (daysElapsed.toFloat() / windowDays).coerceIn(0f, 1f)
            val fbFrac = (feedbackCount.toFloat() / windowDays).coerceIn(0f, 1f)
            val avg = ((dayFrac + fbFrac) / 2f).coerceIn(0f, 1f)
            return LearningProgress(daysElapsed, feedbackCount, avg)
        }
    }
}

@Composable
fun LearningTreeCard(progress: LearningProgress, modifier: Modifier = Modifier) {
    if (progress.isMature) {
        MaturePill(modifier = modifier)
    } else {
        GrowingTreeCard(progress = progress, modifier = modifier)
    }
}

@Composable
private fun GrowingTreeCard(progress: LearningProgress, modifier: Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.progressFraction,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "tree-progress"
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(modifier = Modifier.size(88.dp), contentAlignment = Alignment.Center) {
                TreeCanvas(progress = animatedProgress, modifier = Modifier.size(88.dp))
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Learning your allergies",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                val days = progress.daysElapsed
                val checkIns = progress.feedbackCount
                val checkInLabel = if (checkIns == 1) "check-in" else "check-ins"
                Text(
                    text = "Day $days of 30 · $checkIns $checkInLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tap a leaf below to help me grow",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun MaturePill(modifier: Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TreeCanvas(progress = 1f, modifier = Modifier.size(32.dp))
            Text(
                text = "Fully personalised",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private data class LeafCluster(val xFrac: Float, val yFrac: Float, val radiusFrac: Float, val threshold: Float)

private val LEAF_CLUSTERS = listOf(
    LeafCluster(xFrac = 0.50f, yFrac = 0.55f, radiusFrac = 0.14f, threshold = 0.05f),
    LeafCluster(xFrac = 0.32f, yFrac = 0.42f, radiusFrac = 0.16f, threshold = 0.20f),
    LeafCluster(xFrac = 0.68f, yFrac = 0.42f, radiusFrac = 0.16f, threshold = 0.35f),
    LeafCluster(xFrac = 0.50f, yFrac = 0.28f, radiusFrac = 0.18f, threshold = 0.55f),
    LeafCluster(xFrac = 0.26f, yFrac = 0.30f, radiusFrac = 0.13f, threshold = 0.75f),
    LeafCluster(xFrac = 0.74f, yFrac = 0.30f, radiusFrac = 0.13f, threshold = 0.90f)
)

@Composable
private fun TreeCanvas(progress: Float, modifier: Modifier) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val trunkColor = BarkBrown
    val leafColorEnd = ForestGreen
    val leafColorStart = SpringLeafContainer
    val sunColor = SunlightGold

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val groundY = h * 0.92f

        val trunkHeight = (0.30f + 0.55f * safeProgress) * h
        val trunkTopY = groundY - trunkHeight
        val trunkWidth = w * 0.10f
        val trunkCenterX = w * 0.5f

        drawLine(
            color = trunkColor,
            start = Offset(trunkCenterX, groundY),
            end = Offset(trunkCenterX, trunkTopY),
            strokeWidth = trunkWidth,
            cap = StrokeCap.Round
        )

        // Branches appear once progress > 0.25
        if (safeProgress > 0.25f) {
            val branchProgress = ((safeProgress - 0.25f) / 0.55f).coerceIn(0f, 1f)
            val branchLen = w * 0.22f * branchProgress
            val branchOriginY = trunkTopY + trunkHeight * 0.25f
            val branchStroke = trunkWidth * 0.55f
            drawLine(
                color = trunkColor,
                start = Offset(trunkCenterX, branchOriginY),
                end = Offset(trunkCenterX - branchLen, branchOriginY - branchLen * 0.6f),
                strokeWidth = branchStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = trunkColor,
                start = Offset(trunkCenterX, branchOriginY),
                end = Offset(trunkCenterX + branchLen, branchOriginY - branchLen * 0.6f),
                strokeWidth = branchStroke,
                cap = StrokeCap.Round
            )
        }

        // Leaf clusters, anchored above the trunk top
        val canopyTop = trunkTopY - h * 0.05f
        val canopyHeight = trunkHeight * 0.85f
        LEAF_CLUSTERS.forEach { cluster ->
            if (safeProgress >= cluster.threshold) {
                val localProgress = ((safeProgress - cluster.threshold) / (1f - cluster.threshold)).coerceIn(0f, 1f)
                val cx = w * cluster.xFrac
                val cy = canopyTop + canopyHeight * cluster.yFrac
                val radius = w * cluster.radiusFrac * (0.55f + 0.45f * localProgress)
                val color = lerp(leafColorStart, leafColorEnd, safeProgress)
                val alpha = 0.55f + 0.45f * localProgress
                drawCircle(
                    color = color.copy(alpha = min(1f, alpha)),
                    radius = radius,
                    center = Offset(cx, cy)
                )
            }
        }

        // Sun accent in the top-right, fades in past 0.5
        if (safeProgress > 0.5f) {
            val sunAlpha = ((safeProgress - 0.5f) / 0.5f).coerceIn(0f, 1f)
            drawCircle(
                color = sunColor.copy(alpha = 0.85f * sunAlpha),
                radius = w * 0.07f,
                center = Offset(w * 0.88f, h * 0.12f)
            )
        }
    }
}
