package com.example.taskify.data.local

import android.content.Context
import androidx.room.*
import com.example.taskify.data.model.Note
import com.example.taskify.data.model.Task
import com.example.taskify.data.model.User

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE user_id = :userId ORDER BY due_date ASC")
    suspend fun getAllTasks(userId: String): List<Task>

    @Query("SELECT * FROM tasks WHERE user_id = :userId AND is_completed = 0 ORDER BY due_date ASC")
    suspend fun getIncompleteTasks(userId: String): List<Task>

    @Query("SELECT * FROM tasks WHERE task_id = :taskId")
    suspend fun getTaskById(taskId: Int): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("UPDATE tasks SET is_completed = :isCompleted WHERE task_id = :taskId")
    suspend fun updateTaskCompletion(taskId: Int, isCompleted: Boolean)

    @Query("DELETE FROM tasks WHERE user_id = :userId")
    suspend fun deleteAllUserTasks(userId: String)
}