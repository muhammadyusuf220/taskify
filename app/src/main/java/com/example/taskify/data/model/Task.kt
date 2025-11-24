package com.example.taskify.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "tasks")
data class Task(

    @PrimaryKey(autoGenerate = true)
    val task_id: Int,

    val user_id: Int,

    val firestore_id: String = "",

    val title: String,

    val description: String,

    val due_date: String,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,

    val created_at: String
) {

    // Constructor kosong agar Room TIDAK memakainya
    @Ignore
    constructor() : this(
        0,
        0,
        "",
        "",
        "",
        "",
        false,
        ""
    )
}

