package com.example.todoapp.data

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TodoRepository @Inject constructor(private val dao: TodoDao) {

    fun getTasksForDate(date: String): Flow<List<TodoTask>> = dao.getTasksForDate(date)

    suspend fun addTask(text: String, date: String, sortOrder: Int) {
        dao.insert(TodoTask(text = text, createdDate = date, sortOrder = sortOrder))
    }

    suspend fun toggleTask(task: TodoTask) {
        dao.update(task.copy(isCompleted = !task.isCompleted))
    }

    suspend fun updateTaskText(task: TodoTask, newText: String) {
        dao.update(task.copy(text = newText))
    }

    suspend fun deleteTask(task: TodoTask) {
        dao.delete(task)
    }

    suspend fun updateSortOrders(tasks: List<TodoTask>) {
        dao.updateAll(tasks.mapIndexed { index, task -> task.copy(sortOrder = index) })
    }
}
