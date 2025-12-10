package com.example.taskify.data.api

import com.example.taskify.data.model.Holiday
import retrofit2.http.GET
import retrofit2.http.Query

interface HolidayApiService {
    @GET("api")
    suspend fun getHolidays(
        @Query("year") year: Int
    ): List<Holiday>
}