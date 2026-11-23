package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.PulseAmber
import com.example.ui.theme.PulseCoral
import com.example.ui.theme.PulseMint
import kotlin.math.roundToInt

@Composable
fun ConnectivityStatusPill(
    isSystemOnline: Boolean,
    isOfflineForced: Boolean,
    pendingCount: Int,
    onToggleOfflineMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val effectiveOnline = isSystemOnline && !isOfflineForced
    val containerColor by animateColorAsState(
        targetValue = when {
            !isSystemOnline -> MaterialTheme.colorScheme.errorContainer
            isOfflineForced -> PulseAmber.copy(alpha = 0.2f)
            pendingCount > 0 -> MaterialTheme.colorScheme.primaryContainer
            else -> PulseMint.copy(alpha = 0.16f)
        },
        label = "status_pill_bg"
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !isSystemOnline -> MaterialTheme.colorScheme.onErrorContainer
            isOfflineForced -> Color(0xFFB45309)
            pendingCount > 0 -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> Color(0xFF047857)
        },
        label = "status_pill_fg"
    )

    val label = when {
        !isSystemOnline -> if (pendingCount > 0) "Offline ($pendingCount queued)" else "Device Offline"
        isOfflineForced -> if (pendingCount > 0) "Offline Mode ($pendingCount)" else "Offline Mode"
        pendingCount > 0 -> "Syncing $pendingCount..."
        else -> "Live Sync"
    }

    val icon = when {
        !effectiveOnline -> Icons.Rounded.CloudOff
        pendingCount > 0 -> Icons.Rounded.CloudSync
        else -> Icons.Rounded.CloudDone
    }

    Surface(
        onClick = onToggleOfflineMode,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("offline_mode_toggle_pill")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Toggle offline mode",
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FormAccentHeaderBar(
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        accentColor,
                        accentColor.copy(alpha = 0.72f)
                    )
                )
            )
    )
}

data class ChartSlice(
    val label: String,
    val count: Int,
    val color: Color
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PulseDonutChart(
    slices: List<ChartSlice>,
    totalResponses: Int,
    selectedIndex: Int?,
    onSelectSlice: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSlices = slices.filter { it.count > 0 }
    val totalCount = activeSlices.sumOf { it.count }.coerceAtLeast(1)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(136.dp),
                contentAlignment = Alignment.Center
            ) {
                val animatedProgress by animateFloatAsState(
                    targetValue = if (activeSlices.isEmpty()) 0f else 1f,
                    animationSpec = tween(durationMillis = 550),
                    label = "donut_progress"
                )
                val emptyTrackColor = MaterialTheme.colorScheme.surfaceVariant
                Canvas(modifier = Modifier.size(124.dp)) {
                    val strokeWidth = 24.dp.toPx()
                    val inset = strokeWidth / 2f
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    if (activeSlices.isEmpty()) {
                        drawArc(
                            color = emptyTrackColor,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(width = strokeWidth)
                        )
                    } else {
                        var startAngle = -90f
                        slices.forEachIndexed { index, slice ->
                            if (slice.count > 0) {
                                val sweep = (slice.count.toFloat() / totalCount.toFloat()) * 360f * animatedProgress
                                val isSelected = selectedIndex == index
                                val extraStroke = if (isSelected) 5.dp.toPx() else 0f
                                drawArc(
                                    color = slice.color,
                                    startAngle = startAngle,
                                    sweepAngle = (sweep - 2f).coerceAtLeast(1f),
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = arcSize,
                                    style = Stroke(
                                        width = strokeWidth + extraStroke,
                                        cap = StrokeCap.Butt
                                    )
                                )
                                startAngle += sweep
                            }
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val selectedSlice = selectedIndex?.let { slices.getOrNull(it) }
                    if (selectedSlice != null && selectedSlice.count > 0) {
                        val pct = ((selectedSlice.count * 100f) / totalCount).roundToInt()
                        Text(
                            text = "$pct%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = selectedSlice.color
                        )
                        Text(
                            text = "${selectedSlice.count} votes",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "$totalResponses",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "answers",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                slices.forEachIndexed { index, slice ->
                    val pct = if (totalCount > 0 && slice.count > 0) {
                        ((slice.count * 100f) / totalCount).roundToInt()
                    } else 0
                    val isSelected = selectedIndex == index
                    Surface(
                        color = if (isSelected) slice.color.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp),
                        border = if (isSelected) BorderStroke(1.5.dp, slice.color) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                onSelectSlice(if (isSelected) null else index)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(slice.color)
                                )
                                Text(
                                    text = slice.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${slice.count} ($pct%)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ProgressBarMetric(
    val label: String,
    val count: Int,
    val color: Color
)

@Composable
fun PulseHorizontalProgressBars(
    items: List<ProgressBarMetric>,
    totalResponses: Int,
    modifier: Modifier = Modifier
) {
    val safeTotal = totalResponses.coerceAtLeast(1)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { item ->
            val ratio = (item.count.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
            val animatedRatio by animateFloatAsState(
                targetValue = ratio,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "bar_fill_${item.label}"
            )
            val pct = (ratio * 100f).roundToInt()

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${item.count} ($pct%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animatedRatio)
                            .height(10.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(item.color)
                    )
                }
            }
        }
    }
}

data class HistogramBar(
    val bucketLabel: String,
    val count: Int
)

@Composable
fun PulseDistributionHistogram(
    bars: List<HistogramBar>,
    average: Double,
    maxScale: Int,
    accentColor: Color,
    isStarQuestion: Boolean,
    modifier: Modifier = Modifier
) {
    val maxCount = (bars.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = accentColor.copy(alpha = 0.14f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isStarQuestion) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = PulseAmber,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = String.format("%.1f / %d avg", average, maxScale),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                }
            }
            Text(
                text = "${bars.sumOf { it.count }} rated",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            bars.forEach { bar ->
                val targetFraction = (bar.count.toFloat() / maxCount.toFloat()).coerceIn(0f, 1f)
                val animatedFraction by animateFloatAsState(
                    targetValue = targetFraction,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "hist_bar_${bar.bucketLabel}"
                )
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    Text(
                        text = if (bar.count > 0) "${bar.count}" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val barColor = accentColor
                        val trackColor = MaterialTheme.colorScheme.surfaceVariant
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth(0.68f)
                                .height(64.dp)
                        ) {
                            drawRoundRect(
                                color = trackColor,
                                cornerRadius = CornerRadius(12f, 12f)
                            )
                            val fillH = (size.height * animatedFraction).coerceAtLeast(if (bar.count > 0) 10f else 0f)
                            if (fillH > 0f) {
                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(0f, size.height - fillH),
                                    size = Size(size.width, fillH),
                                    cornerRadius = CornerRadius(12f, 12f)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = bar.bucketLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
