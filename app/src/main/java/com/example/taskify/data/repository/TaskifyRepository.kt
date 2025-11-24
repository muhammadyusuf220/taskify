package com.example.taskify.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.taskify.data.local.AppDatabase
import com.example.taskify.data.model.Note
import com.example.taskify.data.model.Task
import com.example.taskify.data.model.User
import java.security.MessageDigest

class TaskifyRepository(private val database: AppDatabase, context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("taskify_prefs", Context.MODE_PRIVATE)

    // Hash password dengan SHA-256
    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // Auth Methods
    suspend fun registerUser(email: String, password: String, username: String): Result<User> {
        return try {
            // Cek apakah email sudah terdaftar
            val existingUser = database.userDao().getUserByEmail(email)
            if (existingUser != null) {
                return Result.failure(Exception("Email sudah terdaftar"))
            }

            val hashedPassword = hashPassword(password)
            val user = User(
                username = username,
                email = email,
                password = hashedPassword
            )

            val userId = database.userDao().insertUser(user).toInt()
            val newUser = user.copy(user_id = userId)

            // Simpan session
            saveUserSession(userId)

            Result.success(newUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginUser(email: String, password: String): Result<User> {
        return try {
            val user = database.userDao().getUserByEmail(email)
                ?: return Result.failure(Exception("Email tidak ditemukan"))

            val hashedPassword = hashPassword(password)
            if (user.password != hashedPassword) {
                return Result.failure(Exception("Password salah"))
            }

            // Simpan session
            saveUserSession(user.user_id)

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logoutUser() {
        prefs.edit().remove("current_user_id").apply()
    }

    fun getCurrentUserId(): Int? {
        val userId = prefs.getInt("current_user_id", -1)
        return if (userId == -1) null else userId
    }

    private fun saveUserSession(userId: Int) {
        prefs.edit().putInt("current_user_id", userId).apply()
    }

    fun isUserLoggedIn(): Boolean {
        return getCurrentUserId() != null
    }

    // Task Methods
    suspend fun getAllTasks(userId: Int): List<Task> {
        return database.taskDao().getAllTasks(userId.toString())
    }

    suspend fun getIncompleteTasks(userId: Int): List<Task> {
        return database.taskDao().getIncompleteTasks(userId.toString())
    }

    suspend fun insertTask(task: Task): Long {
        return database.taskDao().insertTask(task)
    }

    suspend fun updateTask(task: Task) {
        database.taskDao().updateTask(task)
    }

    suspend fun deleteTask(task: Task) {
        database.taskDao().deleteTask(task)
    }

    suspend fun toggleTaskCompletion(taskId: Int, isCompleted: Boolean) {
        database.taskDao().updateTaskCompletion(taskId, isCompleted)
    }

    // Note Methods
    suspend fun getAllNotes(userId: Int): List<Note> {
        return database.noteDao().getAllNotes(userId)
    }

    suspend fun insertNote(note: Note): Long {
        return database.noteDao().insertNote(note)
    }

    suspend fun updateNote(note: Note) {
        database.noteDao().updateNote(note)
    }

    suspend fun deleteNote(note: Note) {
        database.noteDao().deleteNote(note)
    }
}