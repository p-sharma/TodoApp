package com.example.todoapp.ui.dashboard

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
                if (selectedTab == DashboardTab.PATTERNS) {
                    PatternsTab()
                } else {
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
                                    DashboardTab.STREAKS -> StreaksTab(s.data)
                                    DashboardTab.PATTERNS -> {}
                                }
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
        Text("Last 60 Days", style = MaterialTheme.typography.titleMedium)

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CompletionRingChart(
                rate = stats.completionRate,
                modifier = Modifier.size(160.dp)
            )
        }
        StatCard(
            label = "Overall Completion Rate",
            value = "%.0f%%".format(stats.completionRate * 100),
            modifier = Modifier.fillMaxWidth()
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(label = "Total Tasks Added", value = stats.totalTasks.toString(), modifier = Modifier.weight(1f))
            StatCard(label = "Avg Tasks / Active Day", value = "%.1f".format(stats.avgTasksPerDay), modifier = Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(
                label = "Most Productive Day",
                value = stats.mostProductiveDay?.getDisplayName(TextStyle.SHORT, Locale.getDefault()) ?: "–",
                modifier = Modifier.weight(1f)
            )
            StatCard(
                label = "Least Productive Day",
                value = stats.leastProductiveDay?.getDisplayName(TextStyle.SHORT, Locale.getDefault()) ?: "–",
                modifier = Modifier.weight(1f)
            )
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
private fun PatternsTab(viewModel: PatternsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is PatternsUiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Analyzing task patterns...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        is PatternsUiState.Error -> {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { viewModel.retry() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Couldn't load analysis model. Tap to retry.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }
        is PatternsUiState.Ready -> {
            val sorted = when (state.sortMode) {
                SortMode.ByFrequency -> state.categories.sortedByDescending { it.taskCount }
                SortMode.ByCompletionRate -> state.categories.sortedByDescending { it.completionRate }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Task Categories", style = MaterialTheme.typography.titleMedium)

                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SortMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = state.sortMode == mode,
                            onClick = { viewModel.setSortMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index, SortMode.entries.size),
                            label = { Text(mode.label) }
                        )
                    }
                }

                sorted.forEach { cat ->
                    CategoryCard(cat, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(stat: CategoryStat, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stat.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${stat.taskCount} task${if (stat.taskCount != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "%.0f%%".format(stat.completionRate * 100),
                style = MaterialTheme.typography.headlineSmall
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
