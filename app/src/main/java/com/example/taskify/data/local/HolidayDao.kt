package com.example.taskify.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.taskify.data.model.Holiday
import kotlinx.coroutines.flow.Flow

@Dao
interface HolidayDao {
    // FUNGSI UTAMA: Ambil semua data (2024, 2025, 2026, dst) untuk ditampilkan di Kalender
    @Query("SELECT * FROM holidays")
    fun getAllHolidays(): Flow<List<Holiday>>

    // FUNGSI INSERT: Menyimpan data dari API
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHolidays(holidays: List<Holiday>)

    // FUNGSI CEK: Untuk mengetahui apakah data tahun tertentu sudah ada atau belum
    @Query("SELECT COUNT(*) FROM holidays WHERE date LIKE :year || '%'")
    suspend fun getCountByYear(year: String): Int
}