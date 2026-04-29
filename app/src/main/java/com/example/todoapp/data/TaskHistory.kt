package com.example.todoapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_history")
data class TaskHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val taskText: String,
    val isCompleted: Boolean,
    val sortOrder: Int
)
