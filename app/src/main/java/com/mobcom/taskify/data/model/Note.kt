package com.mobcom.taskify.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true)
    val note_id: Int = 0,
    val cloud_id: String? = null,
    val user_id: String = "",
    val title: String = "",
    val content: String = "",
    val color_tag: String = "#FFFFFF",
    val created_at: String = Date().time.toString(),
    val last_synced: String? = null
)