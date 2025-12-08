package com.example.taskify.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    // Pastikan diakhiri dengan tanda slash '/'
    private const val BASE_URL = "https://api-harilibur.vercel.app/"

    val instance: HolidayApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(HolidayApiService::class.java)
    }
}