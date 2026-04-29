package com.example.todoapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.todoapp.data.DEFAULT_DAILY_LIMIT
import com.example.todoapp.data.PreferencesRepository
import com.example.todoapp.data.TodoRepository
import com.example.todoapp.data.TodoTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

const val MAX_TASK_LENGTH = 120
const val CHAR_COUNT_WARNING_THRESHOLD = 20

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodoViewModel @Inject constructor(
    private val repository: TodoRepository,
    private val prefsRepository: PreferencesRepository
) : ViewModel() {

    private val _today = MutableStateFlow(LocalDate.now().toString())
    val today: StateFlow<String> = _today.asStateFlow()

    val tasks: StateFlow<List<TodoTask>> = _today.flatMapLatest { date ->
        repository.getTasksForDate(date)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyLimit: StateFlow<Int> = prefsRepository.dailyLimit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DEFAULT_DAILY_LIMIT)

    val isAtLimit: StateFlow<Boolean> = combine(tasks, dailyLimit) { taskList, limit ->
        taskList.size >= limit
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch { runOnOpenResetCheck() }
        scheduleMidnightReset()
    }

    private suspend fun runOnOpenResetCheck() {
        val lastReset = prefsRepository.lastResetDate.first()
        val today = LocalDate.now().toString()
        if (lastReset == null) {
            prefsRepository.setLastResetDate(today)
            return
        }
        if (lastReset < today) {
            repository.archiveAndClearOldTasks(today)
            prefsRepository.setLastResetDate(today)
        }
    }

    private fun scheduleMidnightReset() {
        viewModelScope.launch {
            while (isActive) {
                val now = LocalDateTime.now()
                val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
                val millisUntilMidnight = ChronoUnit.MILLIS.between(now, nextMidnight)
                kotlinx.coroutines.delay(millisUntilMidnight)

                val newToday = LocalDate.now().toString()
                repository.archiveAndClearOldTasks(newToday)
                prefsRepository.setLastResetDate(newToday)
                _today.value = newToday
            }
        }
    }

    fun addTask(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_TASK_LENGTH) return
        if (tasks.value.size >= dailyLimit.value) return
        viewModelScope.launch {
            repository.addTask(trimmed, _today.value, tasks.value.size)
        }
    }

    fun toggleTask(task: TodoTask) {
        viewModelScope.launch {
            repository.toggleTask(task)
        }
    }

    fun updateTaskText(task: TodoTask, newText: String) {
        val trimmed = newText.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_TASK_LENGTH) return
        viewModelScope.launch {
            repository.updateTaskText(task, trimmed)
        }
    }

    fun deleteTask(task: TodoTask) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun reorderTasks(tasks: List<TodoTask>) {
        viewModelScope.launch {
            repository.updateSortOrders(tasks)
        }
    }

    fun setDailyLimit(limit: Int) {
        if (limit < 1) return
        viewModelScope.launch {
            prefsRepository.setDailyLimit(limit)
        }
    }
}
