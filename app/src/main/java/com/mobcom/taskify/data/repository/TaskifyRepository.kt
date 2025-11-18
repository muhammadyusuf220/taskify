package com.mobcom.taskify.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mobcom.taskify.data.local.AppDatabase
import com.mobcom.taskify.data.model.Note
import com.mobcom.taskify.data.model.Task
import com.mobcom.taskify.data.model.User
import kotlinx.coroutines.tasks.await
import java.util.*

class TaskifyRepository(private val database: AppDatabase) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    // Auth Methods
    suspend fun registerUser(email: String, password: String, username: String): Result<User> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("User creation failed")

            val user = User(uid = uid, username = username, email = email)
            firestore.collection("users").document(uid)
                .set(mapOf("username" to username, "email" to email))
                .await()

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginUser(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("Login failed")

            val userDoc = firestore.collection("users").document(uid).get().await()
            val username = userDoc.getString("username") ?: ""

            Result.success(User(uid = uid, username = username, email = email))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logoutUser() {
        auth.signOut()
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    // Task Methods
    suspend fun getAllTasks(userId: String): List<Task> {
        return database.taskDao().getAllTasks(userId)
    }

    suspend fun getIncompleteTasks(userId: String): List<Task> {
        return database.taskDao().getIncompleteTasks(userId)
    }

    suspend fun insertTask(task: Task): Long {
        val taskId = database.taskDao().insertTask(task)
        syncTaskToFirebase(task.copy(task_id = taskId.toInt()))
        return taskId
    }

    suspend fun updateTask(task: Task) {
        database.taskDao().updateTask(task)
        syncTaskToFirebase(task)
    }

    suspend fun deleteTask(task: Task) {
        database.taskDao().deleteTask(task)
        task.cloud_id?.let { cloudId ->
            firestore.collection("users").document(task.user_id)
                .collection("tasks").document(cloudId).delete().await()
        }
    }

    suspend fun toggleTaskCompletion(taskId: Int, isCompleted: Boolean) {
        database.taskDao().updateTaskCompletion(taskId, isCompleted)
        val task = database.taskDao().getTaskById(taskId)
        task?.let { syncTaskToFirebase(it.copy(is_completed = isCompleted)) }
    }

    private suspend fun syncTaskToFirebase(task: Task) {
        try {
            val userId = task.user_id
            val taskData = mapOf(
                "title" to task.title,
                "description" to task.description,
                "due_date" to task.due_date,
                "is_completed" to task.is_completed,
                "created_at" to task.created_at
            )

            if (task.cloud_id != null) {
                firestore.collection("users").document(userId)
                    .collection("tasks").document(task.cloud_id)
                    .set(taskData).await()
            } else {
                val docRef = firestore.collection("users").document(userId)
                    .collection("tasks").add(taskData).await()

                database.taskDao().updateTask(
                    task.copy(cloud_id = docRef.id, last_synced = Date().time.toString())
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Note Methods
    suspend fun getAllNotes(userId: String): List<Note> {
        return database.noteDao().getAllNotes(userId)
    }

    suspend fun insertNote(note: Note): Long {
        val noteId = database.noteDao().insertNote(note)
        syncNoteToFirebase(note.copy(note_id = noteId.toInt()))
        return noteId
    }

    suspend fun updateNote(note: Note) {
        database.noteDao().updateNote(note)
        syncNoteToFirebase(note)
    }

    suspend fun deleteNote(note: Note) {
        database.noteDao().deleteNote(note)
        note.cloud_id?.let { cloudId ->
            firestore.collection("users").document(note.user_id)
                .collection("notes").document(cloudId).delete().await()
        }
    }

    private suspend fun syncNoteToFirebase(note: Note) {
        try {
            val userId = note.user_id
            val noteData = mapOf(
                "title" to note.title,
                "content" to note.content,
                "color_tag" to note.color_tag,
                "created_at" to note.created_at
            )

            if (note.cloud_id != null) {
                firestore.collection("users").document(userId)
                    .collection("notes").document(note.cloud_id)
                    .set(noteData).await()
            } else {
                val docRef = firestore.collection("users").document(userId)
                    .collection("notes").add(noteData).await()

                database.noteDao().updateNote(
                    note.copy(cloud_id = docRef.id, last_synced = Date().time.toString())
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}