package com.example.todoapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_tasks")
data class TodoTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val isCompleted: Boolean = false,
    val sortOrder: Int = 0,
    val createdDate: String
)
