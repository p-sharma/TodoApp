package com.example.todoapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todoapp.data.TaskHistory
import com.example.todoapp.data.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class OverviewStats(
    val totalTasks: Int,
    val completedTasks: Int,
    val completionRate: Float,
    val avgTasksPerDay: Float,
    val mostProductiveDay: DayOfWeek?,
    val leastProductiveDay: DayOfWeek?
)

data class DayOfWeekStat(
    val dayOfWeek: DayOfWeek,
    val totalTasks: Int,
    val completedTasks: Int,
    val completionRate: Float
)

data class PatternStats(
    val byDayOfWeek: List<DayOfWeekStat>,
    val mostProductiveDay: DayOfWeek?
)

data class StreakStats(
    val currentStreak: Int,
    val longestStreak: Int,
    val activeDaysLast30: Int,
    val heatmapDays: List<HeatmapDay>,
    val heatmapMinRate: Float,
    val heatmapMaxRate: Float
)

data class HeatmapDay(
    val date: LocalDate,
    val totalTasks: Int,
    val completedTasks: Int
) {
    val hasData: Boolean get() = totalTasks > 0
    val completionRate: Float get() = if (totalTasks > 0) completedTasks.toFloat() / totalTasks else 0f
}

data class DashboardData(
    val daysTracked: Int,
    val overview: OverviewStats,
    val patterns: PatternStats,
    val streaks: StreakStats
)

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Ready(val data: DashboardData) : DashboardUiState
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    repository: TodoRepository
) : ViewModel() {

    val state: StateFlow<DashboardUiState> = repository.getAllHistory()
        .map { history -> DashboardUiState.Ready(compute(history)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState.Loading)

    private fun compute(history: List<TaskHistory>): DashboardData {
        val (heatmapDays, heatmapMinRate, heatmapMaxRate) = computeHeatmap(history)

        if (history.isEmpty()) {
            return DashboardData(
                daysTracked = 0,
                overview = OverviewStats(0, 0, 0f, 0f, null, null),
                patterns = PatternStats(
                    byDayOfWeek = DayOfWeek.entries.map { DayOfWeekStat(it, 0, 0, 0f) },
                    mostProductiveDay = null
                ),
                streaks = StreakStats(0, 0, 0, heatmapDays, heatmapMinRate, heatmapMaxRate)
            )
        }

        val sortedDates = history.map { it.date }.distinct().sorted()
        val daysTracked = sortedDates.size

        // Overview stats are computed over the last 60 days only.
        val cutoff60 = LocalDate.now().minusDays(60)
        val history60 = history.filter { !LocalDate.parse(it.date).isBefore(cutoff60) }
        val daysTracked60 = history60.map { it.date }.distinct().size

        val total60 = history60.size
        val completed60 = history60.count { it.isCompleted }
        val completionRate60 = if (total60 > 0) completed60.toFloat() / total60 else 0f
        val avgTasksPerDay60 = if (daysTracked60 > 0) total60.toFloat() / daysTracked60 else 0f

        val byDow60 = history60.groupBy { LocalDate.parse(it.date).dayOfWeek }
        val dowStats60 = DayOfWeek.entries.map { dow ->
            val tasks = byDow60[dow] ?: emptyList()
            val dowTotal = tasks.size
            val dowCompleted = tasks.count { it.isCompleted }
            DayOfWeekStat(
                dayOfWeek = dow,
                totalTasks = dowTotal,
                completedTasks = dowCompleted,
                completionRate = if (dowTotal > 0) dowCompleted.toFloat() / dowTotal else 0f
            )
        }
        val activeDowStats60 = dowStats60.filter { it.totalTasks > 0 }
        val mostProductiveDay60 = activeDowStats60.maxByOrNull { it.completionRate }?.dayOfWeek
        val leastProductiveDay60 = activeDowStats60.minByOrNull { it.completionRate }?.dayOfWeek

        // Patterns and streaks use all available history.
        val byDow = history.groupBy { LocalDate.parse(it.date).dayOfWeek }
        val dowStats = DayOfWeek.entries.map { dow ->
            val tasks = byDow[dow] ?: emptyList()
            val dowTotal = tasks.size
            val dowCompleted = tasks.count { it.isCompleted }
            DayOfWeekStat(
                dayOfWeek = dow,
                totalTasks = dowTotal,
                completedTasks = dowCompleted,
                completionRate = if (dowTotal > 0) dowCompleted.toFloat() / dowTotal else 0f
            )
        }
        val mostProductiveDay = dowStats
            .filter { it.totalTasks > 0 }
            .maxByOrNull { it.completionRate }
            ?.dayOfWeek

        val localDates = sortedDates.map { LocalDate.parse(it) }
        val currentStreak = computeCurrentStreak(localDates)
        val longestStreak = computeLongestStreak(localDates)
        val cutoff30 = LocalDate.now().minusDays(30)
        val activeLast30 = localDates.count { !it.isBefore(cutoff30) }

        return DashboardData(
            daysTracked = daysTracked,
            overview = OverviewStats(total60, completed60, completionRate60, avgTasksPerDay60, mostProductiveDay60, leastProductiveDay60),
            patterns = PatternStats(dowStats, mostProductiveDay),
            streaks = StreakStats(currentStreak, longestStreak, activeLast30, heatmapDays, heatmapMinRate, heatmapMaxRate)
        )
    }

    private fun computeCurrentStreak(sortedDates: List<LocalDate>): Int {
        if (sortedDates.isEmpty()) return 0
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        if (sortedDates.last() != today && sortedDates.last() != yesterday) return 0
        var streak = 1
        for (i in sortedDates.size - 2 downTo 0) {
            if (ChronoUnit.DAYS.between(sortedDates[i], sortedDates[i + 1]) == 1L) streak++
            else break
        }
        return streak
    }

    private fun computeLongestStreak(sortedDates: List<LocalDate>): Int {
        if (sortedDates.isEmpty()) return 0
        var longest = 1
        var current = 1
        for (i in 1 until sortedDates.size) {
            if (ChronoUnit.DAYS.between(sortedDates[i - 1], sortedDates[i]) == 1L) {
                current++
                if (current > longest) longest = current
            } else {
                current = 1
            }
        }
        return longest
    }

    private fun computeHeatmap(history: List<TaskHistory>): Triple<List<HeatmapDay>, Float, Float> {
        val today = LocalDate.now()
        // Window: Monday of the week 11 weeks before current week → 12 full weeks (84 days)
        val startDate = today.with(DayOfWeek.MONDAY).minusWeeks(11)
        val historyByDate = history.groupBy { it.date }

        val days = (0 until 84).map { i ->
            val date = startDate.plusDays(i.toLong())
            val entries = historyByDate[date.toString()] ?: emptyList()
            HeatmapDay(
                date = date,
                totalTasks = entries.size,
                completedTasks = entries.count { it.isCompleted }
            )
        }

        // Adaptive scale is derived from ALL available history, not just the 12-week window
        val allDayRates = historyByDate.values
            .map { entries -> entries.count { it.isCompleted }.toFloat() / entries.size }

        val minRate = allDayRates.minOrNull() ?: 0f
        val maxRate = allDayRates.maxOrNull() ?: 1f

        return Triple(days, minRate, maxRate)
    }
}
