package com.example.taskify.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore
import java.util.*

@Entity(tableName = "notes")
data class Note(

    @PrimaryKey(autoGenerate = true)
    val note_id: Int = 0,

    val user_id: Int = 0,
    val firestore_id: String = "",
    val title: String = "",
    val content: String = "",
    val color_tag: String = "#FFFFFF",
    val created_at: String = Date().time.toString()

) {
    // Constructor kosong untuk Room
    @Ignore
    constructor() : this(
        0,
        0,
        "",
        "",
        "",
        "#FFFFFF",
        Date().time.toString()
    )
}
