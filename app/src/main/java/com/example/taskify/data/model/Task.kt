// File: com/example/taskify/data/model/Task.kt
package com.example.taskify.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true)
    val task_id: Int = 0,

    val user_id: Int,
    val firestore_id: String = "",
    val title: String,
    val description: String,
    val due_date: String,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,

    val created_at: String,

    // TAMBAHAN BARU:
    @ColumnInfo(name = "is_synced")
    val is_synced: Boolean = false,

    @ColumnInfo(name = "is_deleted")
    val is_deleted: Boolean = false
) {
    // Update constructor kosong
    @Ignore
    constructor() : this(0, 0, "", "", "", "", false, "", false, false)
}