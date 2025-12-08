package com.example.taskify.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "holidays")
data class Holiday(
    @PrimaryKey
    @SerializedName("holiday_date") // <--- PENTING: API baru pakai key ini
    val date: String,

    @SerializedName("holiday_name") // <--- PENTING: API baru pakai key ini
    val localName: String,

    @SerializedName("is_national_holiday")
    val isNationalHoliday: Boolean = false
)