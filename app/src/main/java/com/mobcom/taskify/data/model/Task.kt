package com.mobcom.taskify.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

// Task Model
@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val task_id: Int = 0,
    val cloud_id: String? = null,
    val user_id: String = "",
    val title: String = "",
    val description: String = "",
    val due_date: String = "",
    val is_completed: Boolean = false,
    val created_at: String = Date().time.toString(),
    val last_synced: String? = null
)