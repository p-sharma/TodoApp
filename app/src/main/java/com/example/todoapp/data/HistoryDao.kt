package com.example.todoapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert
    suspend fun insertAll(tasks: List<TaskHistory>)

    @Query("DELETE FROM task_history WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)

    @Query("SELECT * FROM task_history ORDER BY date ASC")
    fun getAllHistory(): Flow<List<TaskHistory>>

    @Query("DELETE FROM task_history")
    suspend fun deleteAll()
}
