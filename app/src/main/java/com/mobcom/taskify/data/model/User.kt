package com.mobcom.taskify.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = ""
)