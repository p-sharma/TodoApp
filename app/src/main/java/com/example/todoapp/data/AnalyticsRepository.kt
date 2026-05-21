package com.example.todoapp.data

import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

@Singleton
class AnalyticsRepository @Inject constructor(
    private val todoRepository: TodoRepository,
    private val categoryMapper: CategoryMapper
) {

    /**
     * Returns the notification body text, or null if the notification should be skipped
     * (insufficient history or no meaningful week-over-week change).
     */
    suspend fun buildWeeklyInsightCopy(): String? {
        val history = todoRepository.getAllHistory().first()
        if (history.map { it.date }.distinct().size < 7) return null

        val today = LocalDate.now()
        val periodEnd = today.minusDays(1)
        val periodStart = periodEnd.minusDays(6)
        val priorEnd = periodStart.minusDays(1)
        val priorStart = priorEnd.minusDays(6)

        val thisWeek = history.filter {
            val d = LocalDate.parse(it.date)
            !d.isBefore(periodStart) && !d.isAfter(periodEnd)
        }
        val priorWeek = history.filter {
            val d = LocalDate.parse(it.date)
            !d.isBefore(priorStart) && !d.isAfter(priorEnd)
        }

        if (thisWeek.isEmpty()) return null

        val currentRate = thisWeek.count { it.isCompleted }.toFloat() / thisWeek.size
        val priorRate = if (priorWeek.isNotEmpty())
            priorWeek.count { it.isCompleted }.toFloat() / priorWeek.size
        else null

        val bestDay = bestDayOf(thisWeek.groupBy { LocalDate.parse(it.date).dayOfWeek })
        val priorBestDay = if (priorWeek.isNotEmpty())
            bestDayOf(priorWeek.groupBy { LocalDate.parse(it.date).dayOfWeek })
        else null

        val rateChange = if (priorRate != null) abs(currentRate - priorRate) else 0f
        val dayChanged = priorBestDay != null && bestDay != priorBestDay
        val categoryInsight = buildCategoryInsight(thisWeek, priorWeek)
        val catChange = categoryInsight?.rateChange ?: 0f

        // Skip if no meaningful change detected week-over-week.
        if (priorRate != null && rateChange < 0.05f && !dayChanged && catChange < 0.05f) return null

        val pct = (currentRate * 100).roundToInt()
        val priorPct = priorRate?.let { (it * 100).roundToInt() }
        val dayName = bestDay?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: "today"

        // Select variant for the dimension with the largest delta.
        return when {
            priorRate != null && rateChange > catChange && currentRate - priorRate >= 0.10f ->
                "You improved! Last week you completed $pct% of your tasks, up from $priorPct% the week before."
            categoryInsight != null && catChange > rateChange && catChange >= 0.05f ->
                categoryInsight.copy
            dayChanged ->
                "Last week you completed $pct% of your tasks. $dayName was your most productive day."
            else ->
                "Last week you completed $pct% of your tasks. Your best day was $dayName."
        }
    }

    private data class CategoryInsightData(val copy: String, val rateChange: Float)

    private suspend fun buildCategoryInsight(
        thisWeek: List<TaskHistory>,
        priorWeek: List<TaskHistory>
    ): CategoryInsightData? = try {
        val extractor = EntityExtraction.getClient(
            EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
        )
        extractor.downloadModelIfNeeded().await()

        val thisStats = categorizeRates(thisWeek, extractor)
        val priorStats = if (priorWeek.isNotEmpty()) categorizeRates(priorWeek, extractor) else emptyMap()
        extractor.close()

        val best = thisStats.maxByOrNull { (cat, rate) ->
            abs(rate - (priorStats[cat] ?: 0f))
        } ?: return null

        val (topCat, topRate) = best
        val priorCatRate = priorStats[topCat] ?: 0f
        val change = abs(topRate - priorCatRate)
        if (change < 0.05f) return null

        val pct = (topRate * 100).roundToInt()
        val direction = if (topRate >= priorCatRate) "up" else "down"
        val deltaPct = (change * 100).roundToInt()
        CategoryInsightData(
            copy = "Last week your $topCat tasks had a $pct% completion rate, $direction $deltaPct% from the week before.",
            rateChange = change
        )
    } catch (_: Exception) {
        null
    }

    private suspend fun categorizeRates(
        tasks: List<TaskHistory>,
        extractor: EntityExtractor
    ): Map<String, Float> {
        val buckets = mutableMapOf<String, MutableList<TaskHistory>>()
        for (task in tasks) {
            val annotations = try {
                extractor.annotate(task.taskText).await()
            } catch (_: Exception) {
                emptyList()
            }
            val categories = annotations
                .flatMap { ann -> ann.getEntities().mapNotNull { e -> categoryMapper.entityTypeToCategory(e.getType()) } }
                .distinct()
            if (categories.isEmpty()) {
                buckets.getOrPut(CategoryMapper.OPEN_ENDED) { mutableListOf() }.add(task)
            } else {
                for (cat in categories) buckets.getOrPut(cat) { mutableListOf() }.add(task)
            }
        }
        return buckets.mapValues { (_, list) ->
            list.count { it.isCompleted }.toFloat() / list.size
        }
    }

    private fun bestDayOf(byDow: Map<DayOfWeek, List<TaskHistory>>): DayOfWeek? =
        byDow.maxByOrNull { (_, tasks) -> tasks.count { it.isCompleted }.toFloat() / tasks.size }?.key
}
