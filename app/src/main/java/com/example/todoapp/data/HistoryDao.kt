package com.example.todoapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface HistoryDao {
    @Insert
    suspend fun insertAll(tasks: List<TaskHistory>)

    @Query("DELETE FROM task_history WHERE date < :cutoffDate")
    suspend fun deleteOlderThan(cutoffDate: String)
}
