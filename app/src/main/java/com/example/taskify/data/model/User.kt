package com.example.taskify.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Ignore
import java.util.*

@Entity(tableName = "users")
data class User(

    @PrimaryKey(autoGenerate = true)
    val user_id: Int = 0,

    val username: String = "",
    val email: String = "",
    val password: String = "",
    val created_at: String = Date().time.toString()

) {
    // Constructor kosong untuk Room (WAJIB)
    @Ignore
    constructor() : this(
        0,
        "",
        "",
        "",
        Date().time.toString()
    )
}
