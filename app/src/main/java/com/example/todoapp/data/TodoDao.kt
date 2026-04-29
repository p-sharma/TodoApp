package com.example.todoapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM daily_tasks WHERE createdDate = :date ORDER BY isCompleted ASC, sortOrder ASC")
    fun getTasksForDate(date: String): Flow<List<TodoTask>>

    @Insert
    suspend fun insert(task: TodoTask)

    @Update
    suspend fun update(task: TodoTask)

    @Update
    suspend fun updateAll(tasks: List<TodoTask>)

    @Delete
    suspend fun delete(task: TodoTask)
}
