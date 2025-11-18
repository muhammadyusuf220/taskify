package com.mobcom.taskify.data.local

import android.content.Context
import androidx.room.*
import com.mobcom.taskify.data.model.Note
import com.mobcom.taskify.data.model.Task

@Database(entities = [Task::class, Note::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "taskify_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}