package com.example.taskify.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.taskify.data.local.AppDatabase
import com.example.taskify.data.model.Note
import com.example.taskify.data.model.Task
import com.example.taskify.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex 
import kotlinx.coroutines.sync.withLock 
import kotlinx.coroutines.flow.Flow
import com.example.taskify.data.api.RetrofitClient 
import com.example.taskify.data.model.Holiday 
import java.text.SimpleDateFormat 
import java.util.Locale          

class TaskifyRepository(private val database: AppDatabase, context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("taskify_prefs", Context.MODE_PRIVATE)

    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val syncMutex = Mutex()
    suspend fun registerUser(email: String, password: String, username: String): Result<User> {
        return try {
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Gagal membuat user Firebase")

            val newUser = User(
                username = username,
                email = email,
                password = "firebase_managed"
            )

            val existingLocalUser = database.userDao().getUserByEmail(email)

            val userId: Int
            if (existingLocalUser == null) {
                userId = database.userDao().insertUser(newUser).toInt()
            } else {
                userId = existingLocalUser.user_id
            }

            val finalUser = newUser.copy(user_id = userId)

            saveUserSession(userId)

            Result.success(finalUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginUser(email: String, password: String): Result<User> {
        return try {
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Gagal login Firebase")

            var localUser = database.userDao().getUserByEmail(email)

            if (localUser == null) {
                val newUser = User(
                    username = firebaseUser.displayName ?: "User",
                    email = email,
                    password = "firebase_managed"
                )
                val newId = database.userDao().insertUser(newUser).toInt()
                localUser = newUser.copy(user_id = newId)
            }

            saveUserSession(localUser.user_id)

            Result.success(localUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logoutUser() {
        firebaseAuth.signOut() 
        prefs.edit().remove("current_user_id").apply() 
    }

    fun getCurrentUserId(): Int? {
        val userId = prefs.getInt("current_user_id", -1)
        return if (userId == -1) null else userId
    }

    private fun saveUserSession(userId: Int) {
        prefs.edit().putInt("current_user_id", userId).commit()
    }

    fun isUserLoggedIn(): Boolean {
        return getCurrentUserId() != null
    }

    fun getAllTasks(userId: Int): Flow<List<Task>> {
        return database.taskDao().getAllTasks(userId)
    }

     fun getIncompleteTasks(userId: Int): Flow<List<Task>> {
        return database.taskDao().getIncompleteTasks(userId)
    }

    suspend fun insertTask(task: Task): Long {
        val newId = database.taskDao().insertTask(task)

        val currentUser = firebaseAuth.currentUser
        if (currentUser != null && task.firestore_id.isNotEmpty()) {
            try {
                firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("tasks")
                    .document(task.firestore_id)
                    .set(
                        hashMapOf(
                            "firestore_id" to task.firestore_id,
                            "title" to task.title,
                            "description" to task.description,
                            "due_date" to task.due_date,
                            "is_completed" to task.isCompleted,
                            "created_at" to task.created_at
                        )
                    ).await()
                database.taskDao().markAsSynced(newId.toInt())

            } catch (e: Exception) {
                e.printStackTrace()

            }
        }

        return newId
    }

    suspend fun updateTask(task: Task) {
        val taskToUpdate = task.copy(is_synced = false)
        database.taskDao().updateTask(taskToUpdate) 

        val currentUser = firebaseAuth.currentUser
        if (currentUser != null && task.firestore_id.isNotEmpty()) {
            try {
                firestore.collection("users").document(currentUser.uid)
                    .collection("tasks").document(task.firestore_id)
                    .set(hashMapOf( 
                        "firestore_id" to task.firestore_id,
                        "title" to task.title,
                        "description" to task.description,
                        "due_date" to task.due_date,
                        "is_completed" to task.isCompleted,
                        "created_at" to task.created_at
                    )).await()
                database.taskDao().markAsSynced(task.task_id)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    suspend fun deleteTask(task: Task) {
        val deletedTask = task.copy(is_deleted = true, is_synced = false)
        database.taskDao().updateTask(deletedTask) 
    }

    suspend fun toggleTaskCompletion(task: Task, isCompleted: Boolean) {
        database.taskDao().updateTaskCompletion(task.task_id, isCompleted)

        val currentUser = firebaseAuth.currentUser
        if (currentUser != null && task.firestore_id.isNotEmpty()) {
            try {
                firestore.collection("users").document(currentUser.uid)
                    .collection("tasks").document(task.firestore_id)
                    .update("is_completed", isCompleted).await()
                database.taskDao().markAsSynced(task.task_id)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private suspend fun uploadUnsyncedTasks(userId: Int) {
        val currentUser = firebaseAuth.currentUser ?: return

        val unsyncedTasks = database.taskDao().getUnsyncedTasks(userId)

        for (task in unsyncedTasks) {
            try {
                val docRef = firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("tasks")
                    .document()

                val firestoreId = docRef.id

                val taskMap = hashMapOf(
                    "firestore_id" to firestoreId,
                    "title" to task.title,
                    "description" to task.description,
                    "due_date" to task.due_date,
                    "is_completed" to task.isCompleted,
                    "created_at" to task.created_at
                )

                docRef.set(taskMap).await()

                val updatedTask = task.copy(firestore_id = firestoreId)
                database.taskDao().updateTask(updatedTask)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun syncTasks(userId: Int) {
        syncMutex.withLock {
            val currentUser = firebaseAuth.currentUser ?: return@withLock

            val unsyncedTasks = database.taskDao().getUnsyncedTasks(userId)
            for (task in unsyncedTasks) {
                try {
                    firestore.collection("users").document(currentUser.uid)
                        .collection("tasks").document(task.firestore_id)
                        .set(hashMapOf(
                            "firestore_id" to task.firestore_id,
                            "title" to task.title,
                            "description" to task.description,
                            "due_date" to task.due_date,
                            "is_completed" to task.isCompleted,
                            "created_at" to task.created_at
                        )).await()
                    database.taskDao().markAsSynced(task.task_id)
                } catch (e: Exception) { e.printStackTrace() }
            }

            val deletedTasks = database.taskDao().getDeletedTasks(userId)
            for (task in deletedTasks) {
                try {
                    if (task.firestore_id.isNotEmpty()) {
                        firestore.collection("users").document(currentUser.uid)
                            .collection("tasks").document(task.firestore_id)
                            .delete().await()
                    }
                    database.taskDao().deleteTask(task) 
                } catch (e: Exception) { e.printStackTrace() }
            }

            try {
                val snapshot = firestore.collection("users")
                    .document(currentUser.uid).collection("tasks").get().await()

                for (doc in snapshot.documents) {
                    val fId = doc.id
                    val data = doc.data ?: continue
                    val localTask = database.taskDao().getTaskByFirestoreId(fId)

                    if (localTask != null && !localTask.is_synced) continue

                    val taskToSave = Task(
                        task_id = localTask?.task_id ?: 0,
                        user_id = userId,
                        firestore_id = fId,
                        title = data["title"] as? String ?: "",
                        description = data["description"] as? String ?: "",
                        due_date = data["due_date"] as? String ?: "",
                        isCompleted = data["is_completed"] as? Boolean ?: false,
                        created_at = data["created_at"] as? String ?: "",
                        is_synced = true, 
                        is_deleted = false
                    )

                    if (localTask == null) database.taskDao().insertTask(taskToSave)
                    else database.taskDao().updateTask(taskToSave)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun getAllNotes(userId: Int): Flow<List<Note>> {
        return database.noteDao().getAllNotes(userId)
    }

    suspend fun insertNote(note: Note): Long {
        val newId = database.noteDao().insertNote(note)

        val currentUser = firebaseAuth.currentUser
        if (currentUser != null && note.firestore_id.isNotEmpty()) {
            try {
                firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("notes")
                    .document(note.firestore_id)
                    .set(
                        hashMapOf(
                            "firestore_id" to note.firestore_id,
                            "title" to note.title,
                            "content" to note.content,
                            "color_tag" to note.color_tag,
                            "created_at" to note.created_at
                        )
                    ).await()

                database.noteDao().markAsSynced(newId.toInt())

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return newId
    }

    private suspend fun uploadUnsyncedNotes(userId: Int) {
        val currentUser = firebaseAuth.currentUser ?: return

        val unsyncedNotes = database.noteDao().getUnsyncedNotes(userId)

        for (note in unsyncedNotes) {
            try {
                firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("notes")
                    .document(note.firestore_id)
                    .set(
                        hashMapOf(
                            "firestore_id" to note.firestore_id,
                            "title" to note.title,
                            "content" to note.content,
                            "color_tag" to note.color_tag,
                            "created_at" to note.created_at
                        )
                    ).await()

                database.noteDao().markAsSynced(note.note_id)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun updateNote(note: Note) {
        val noteToUpdate = note.copy(is_synced = false)
        database.noteDao().updateNote(noteToUpdate)

        val currentUser = firebaseAuth.currentUser
        if (currentUser != null && note.firestore_id.isNotEmpty()) {
            try {
                firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("notes")
                    .document(note.firestore_id)
                    .set( 
                        hashMapOf(
                            "firestore_id" to note.firestore_id,
                            "title" to note.title,
                            "content" to note.content,
                            "color_tag" to note.color_tag,
                            "created_at" to note.created_at
                        )
                    ).await()

                database.noteDao().markAsSynced(note.note_id)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun deleteNote(note: Note) {
        val deletedNote = note.copy(
            is_deleted = true,
            is_synced = false
        )

        database.noteDao().updateNote(deletedNote)
    }

    suspend fun syncNotes(userId: Int) {
        syncMutex.withLock {
            uploadUnsyncedNotes(userId)

            uploadDeletedNotes(userId)

            val currentUser = firebaseAuth.currentUser ?: return@withLock
            try {
                val snapshot = firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("notes")
                    .get()
                    .await()

                for (document in snapshot.documents) {
                    val firestoreId = document.id
                    val data = document.data ?: continue
                    val localNote = database.noteDao().getNoteByFirestoreId(firestoreId)

                    val noteToSave = Note(
                        note_id = localNote?.note_id ?: 0,
                        user_id = userId,
                        firestore_id = firestoreId,
                        title = data["title"] as? String ?: "",
                        content = data["content"] as? String ?: "",
                        color_tag = data["color_tag"] as? String ?: "#FFFFFF",
                        created_at = data["created_at"] as? String ?: ""
                    )

                    if (localNote == null) {
                        database.noteDao().insertNote(noteToSave)
                    } else {
                        database.noteDao().updateNote(noteToSave)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun uploadDeletedNotes(userId: Int) {
        val currentUser = firebaseAuth.currentUser ?: return

        val deletedNotes = database.noteDao().getDeletedNotes(userId)

        for (note in deletedNotes) {
            try {
                if (note.firestore_id.isNotEmpty()) {
                    firestore.collection("users")
                        .document(currentUser.uid)
                        .collection("notes")
                        .document(note.firestore_id)
                        .delete()
                        .await()
                }

                database.noteDao().deleteNote(note)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    suspend fun recoverSessionByEmail(email: String): Boolean {
        return try {
            val localUser = database.userDao().getUserByEmail(email)

            if (localUser != null) {
                saveUserSession(localUser.user_id)
                return true
            }

            val firebaseUser = firebaseAuth.currentUser

            if (firebaseUser != null && firebaseUser.email == email) {
                val newUser = User(
                    username = firebaseUser.displayName ?: "User",
                    email = email,
                    password = "firebase_managed"
                )
                val newId = database.userDao().insertUser(newUser).toInt()

                saveUserSession(newId)

                syncTasks(newId)
                syncNotes(newId)

                return true
            }

            return false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: TaskifyRepository? = null

        fun getInstance(database: AppDatabase, context: Context): TaskifyRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = TaskifyRepository(database, context)
                INSTANCE = instance
                instance
            }
        }
    }

    fun getHolidays(): Flow<List<Holiday>> {
        return database.holidayDao().getAllHolidays()
    }

    suspend fun syncHolidays() {
        val calendar = java.util.Calendar.getInstance()
        val currentYear = calendar.get(java.util.Calendar.YEAR)

        val yearsToFetch = listOf(currentYear - 1, currentYear, currentYear + 1)

        for (year in yearsToFetch) {
            try {
                val count = database.holidayDao().getCountByYear(year.toString())

                if (count < 1) {
                    try {
                        val response = RetrofitClient.instance.getHolidays(year)

                        val fixedList = response.map { holiday ->
                            holiday.copy(
                                date = normalizeDate(holiday.date)
                            )
                        }

                        database.holidayDao().insertHolidays(fixedList)

                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun normalizeDate(rawDate: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-M-d", Locale.getDefault())

            val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val date = inputFormat.parse(rawDate)
            outputFormat.format(date!!)
        } catch (e: Exception) {
            rawDate
        }
    }
}