package com.example.todoapp.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todoapp.data.CategoryMapper
import com.example.todoapp.data.TaskHistory
import com.example.todoapp.data.TodoRepository
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import javax.inject.Inject

enum class SortMode(val label: String) {
    ByFrequency("By Frequency"),
    ByCompletionRate("By Completion Rate")
}

data class CategoryStat(
    val name: String,
    val taskCount: Int,
    val completedCount: Int,
    val completionRate: Float
)

sealed interface PatternsUiState {
    data object Loading : PatternsUiState
    data class Ready(val categories: List<CategoryStat>, val sortMode: SortMode) : PatternsUiState
    data object Error : PatternsUiState
}

@HiltViewModel
class PatternsViewModel @Inject constructor(
    private val repository: TodoRepository,
    private val categoryMapper: CategoryMapper
) : ViewModel() {

    private val _uiState = MutableStateFlow<PatternsUiState>(PatternsUiState.Loading)
    val uiState: StateFlow<PatternsUiState> = _uiState.asStateFlow()

    init {
        analyze()
    }

    fun retry() {
        _uiState.value = PatternsUiState.Loading
        analyze()
    }

    fun setSortMode(mode: SortMode) {
        val current = _uiState.value as? PatternsUiState.Ready ?: return
        _uiState.value = current.copy(sortMode = mode)
    }

    private fun analyze() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val history = repository.getAllHistory().first()
                val cutoff = LocalDate.now().minusDays(60)
                val history60 = history.filter { !LocalDate.parse(it.date).isBefore(cutoff) }

                val extractor = EntityExtraction.getClient(
                    EntityExtractorOptions.Builder(EntityExtractorOptions.ENGLISH).build()
                )
                extractor.downloadModelIfNeeded().await()

                val stats = extractCategories(history60, extractor)
                extractor.close()

                _uiState.value = PatternsUiState.Ready(stats, SortMode.ByFrequency)
            } catch (_: Exception) {
                _uiState.value = PatternsUiState.Error
            }
        }
    }

    private suspend fun extractCategories(
        history: List<TaskHistory>,
        extractor: EntityExtractor
    ): List<CategoryStat> {
        val buckets = mutableMapOf<String, MutableList<TaskHistory>>()
        buckets[CategoryMapper.OPEN_ENDED] = mutableListOf()

        for (task in history) {
            val annotations: List<EntityAnnotation> = try {
                extractor.annotate(task.taskText).await()
            } catch (_: Exception) {
                emptyList()
            }

            val categories = annotations
                .flatMap { annotation ->
                    annotation.getEntities().mapNotNull { entity -> categoryMapper.entityTypeToCategory(entity.getType()) }
                }
                .distinct()

            if (categories.isEmpty()) {
                buckets[CategoryMapper.OPEN_ENDED]!!.add(task)
            } else {
                for (cat in categories) {
                    buckets.getOrPut(cat) { mutableListOf() }.add(task)
                }
            }
        }

        return buckets.map { (name, tasks) ->
            val completed = tasks.count { it.isCompleted }
            CategoryStat(
                name = name,
                taskCount = tasks.size,
                completedCount = completed,
                completionRate = if (tasks.isNotEmpty()) completed.toFloat() / tasks.size else 0f
            )
        }.sortedByDescending { it.taskCount }
    }

}
