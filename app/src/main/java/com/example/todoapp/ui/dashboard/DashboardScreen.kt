package com.example.todoapp.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

enum class DashboardTab(val label: String) {
    OVERVIEW("Overview"),
    PATTERNS("Patterns"),
    STREAKS("Streaks")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardOverlay(
    onClose: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    BackHandler(onBack = onClose)

    var selectedTab by remember { mutableStateOf(DashboardTab.OVERVIEW) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Insights") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close insights")
                    }
                }
            )

            TabRow(selectedTabIndex = selectedTab.ordinal) {
                DashboardTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label) }
                    )
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                when (val s = state) {
                    is DashboardUiState.Loading -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    is DashboardUiState.Ready -> {
                        if (s.data.daysTracked < 7) {
                            EmptyStateContent(s.data.daysTracked)
                        } else {
                            when (selectedTab) {
                                DashboardTab.OVERVIEW -> OverviewTab(s.data)
                                DashboardTab.PATTERNS -> PatternsTab(s.data)
                                DashboardTab.STREAKS -> StreaksTab(s.data)
                            }
                        }
                    }
                }
            }

            HorizontalDivider()
            Text(
                text = "All insights are computed on your device and never leave it.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun EmptyStateContent(daysTracked: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.BarChart,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Keep planning!",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Insights unlock after 7 days.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        val displayDay = if (daysTracked == 0) 1 else daysTracked
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Text(
                text = "Day $displayDay of 7",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun OverviewTab(data: DashboardData) {
    val stats = data.overview
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Completion Rate", style = MaterialTheme.typography.titleMedium)

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CompletionRingChart(
                rate = stats.completionRate,
                modifier = Modifier.size(160.dp)
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(label = "Total Tasks", value = stats.totalTasks.toString(), modifier = Modifier.weight(1f))
            StatCard(label = "Completed", value = stats.completedTasks.toString(), modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(label = "Avg / Day", value = "%.1f".format(stats.avgTasksPerDay), modifier = Modifier.weight(1f))
            StatCard(label = "Days Tracked", value = data.daysTracked.toString(), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CompletionRingChart(rate: Float, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val onSurface = MaterialTheme.colorScheme.onSurface
    val textStyle = MaterialTheme.typography.headlineMedium.copy(color = onSurface)
    val textMeasurer = rememberTextMeasurer()
    val label = "%.0f%%".format(rate * 100)

    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.12f
        val halfStroke = strokeWidth / 2f
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val arcOffset = Offset(halfStroke, halfStroke)

        drawArc(
            color = track,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = arcOffset,
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        if (rate > 0f) {
            drawArc(
                color = primary,
                startAngle = -90f,
                sweepAngle = rate * 360f,
                useCenter = false,
                topLeft = arcOffset,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        val measured = textMeasurer.measure(label, textStyle)
        drawText(
            measured,
            topLeft = Offset(
                (size.width - measured.size.width) / 2f,
                (size.height - measured.size.height) / 2f
            )
        )
    }
}

@Composable
private fun PatternsTab(data: DashboardData) {
    val patterns = data.patterns
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Completion Rate by Day", style = MaterialTheme.typography.titleMedium)

        DayOfWeekBarChart(
            stats = patterns.byDayOfWeek,
            highlightedDay = patterns.mostProductiveDay,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )

        patterns.mostProductiveDay?.let { day ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Most productive: ${day.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        } ?: Text(
            text = "No pattern data yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Task Volume by Day", style = MaterialTheme.typography.titleMedium)

        patterns.byDayOfWeek.filter { it.totalTasks > 0 }.forEach { stat ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stat.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(48.dp)
                )
                Text(
                    text = "${stat.totalTasks} tasks · ${stat.completedTasks} done",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (patterns.byDayOfWeek.none { it.totalTasks > 0 }) {
            Text(
                text = "No task data available yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DayOfWeekBarChart(
    stats: List<DayOfWeekStat>,
    highlightedDay: DayOfWeek?,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryMuted = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = onSurfaceVariant)

    Canvas(modifier = modifier) {
        val labelHeight = 32.dp.toPx()
        val chartHeight = size.height - labelHeight
        val barCount = stats.size
        if (barCount == 0 || chartHeight <= 0f) return@Canvas
        val totalBarWidth = size.width / barCount
        val barWidth = totalBarWidth * 0.55f
        val barStartX = (totalBarWidth - barWidth) / 2f

        stats.forEachIndexed { index, stat ->
            val x = index * totalBarWidth
            val barH = chartHeight * stat.completionRate
            val barTop = chartHeight - barH
            val barColor = if (stat.dayOfWeek == highlightedDay) primary else primaryMuted

            drawRect(
                color = track,
                topLeft = Offset(x + barStartX, 0f),
                size = Size(barWidth, chartHeight)
            )

            if (stat.completionRate > 0f) {
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(x + barStartX, barTop),
                    size = Size(barWidth, barH),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
            }

            val label = stat.dayOfWeek.name.let { it[0].toString() + it[1].lowercase() }
            val measured = textMeasurer.measure(label, labelStyle)
            drawText(
                measured,
                topLeft = Offset(
                    x + totalBarWidth / 2f - measured.size.width / 2f,
                    chartHeight + 8.dp.toPx()
                )
            )
        }
    }
}

@Composable
private fun StreaksTab(data: DashboardData) {
    val streaks = data.streaks
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Your Streaks", style = MaterialTheme.typography.titleMedium)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StreakCard(
                value = streaks.currentStreak.toString(),
                label = "Current Streak",
                unit = if (streaks.currentStreak == 1) "day" else "days",
                modifier = Modifier.weight(1f)
            )
            StreakCard(
                value = streaks.longestStreak.toString(),
                label = "Longest Streak",
                unit = if (streaks.longestStreak == 1) "day" else "days",
                modifier = Modifier.weight(1f)
            )
        }

        StatCard(
            label = "Active days in last 30",
            value = streaks.activeDaysLast30.toString(),
            modifier = Modifier.fillMaxWidth()
        )

        when {
            streaks.currentStreak == 0 -> Text(
                text = "Plan something today to start your streak!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            streaks.currentStreak >= 7 -> Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    text = "You're on a ${streaks.currentStreak}-day streak! Keep it up!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

@Composable
private fun StreakCard(value: String, label: String, unit: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
        }
    }
}
