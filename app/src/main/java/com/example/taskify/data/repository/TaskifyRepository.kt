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
import kotlinx.coroutines.sync.Mutex // <--- Tambah Import
import kotlinx.coroutines.sync.withLock // <--- Tambah Import

class TaskifyRepository(private val database: AppDatabase, context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("taskify_prefs", Context.MODE_PRIVATE)

    // Inisialisasi Firebase Auth
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()

    // --- AUTH METHODS DENGAN FIREBASE ---
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val syncMutex = Mutex()
    suspend fun registerUser(email: String, password: String, username: String): Result<User> {
        return try {
            // 1. Buat user di Firebase
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Gagal membuat user Firebase")

            // 2. Simpan data user ke Database Lokal (Room)
            // Kita tetap butuh user di Room karena Task & Note terhubung ke user_id (Int)
            // Password tidak perlu disimpan di lokal lagi, atau isi dummy saja.
            val newUser = User(
                username = username,
                email = email,
                password = "firebase_managed" // Password dihandle Firebase
            )

            // Cek dulu apakah email sudah ada di lokal (mencegah crash)
            val existingLocalUser = database.userDao().getUserByEmail(email)

            val userId: Int
            if (existingLocalUser == null) {
                userId = database.userDao().insertUser(newUser).toInt()
            } else {
                // Jika entah kenapa di lokal sudah ada tapi di firebase belum
                userId = existingLocalUser.user_id
            }

            // 3. Update objek user dengan ID dari Room
            val finalUser = newUser.copy(user_id = userId)

            // 4. Simpan Session Lokal
            saveUserSession(userId)

            Result.success(finalUser)
        } catch (e: Exception) {
            // Menangkap error Firebase (misal: email format salah, password terlalu pendek, sinyal mati)
            Result.failure(e)
        }
    }

    suspend fun loginUser(email: String, password: String): Result<User> {
        return try {
            // 1. Login ke Firebase
            val authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
            val firebaseUser = authResult.user ?: throw Exception("Gagal login Firebase")

            // 2. Sinkronisasi dengan Database Lokal
            // Cari user di database lokal berdasarkan email
            var localUser = database.userDao().getUserByEmail(email)

            // Jika user sukses login di Firebase tapi data tidak ada di HP ini (misal ganti HP/instal ulang)
            // Kita harus buat data user lokal baru agar Task/Note bisa disimpan.
            if (localUser == null) {
                val newUser = User(
                    username = firebaseUser.displayName ?: "User", // Atau ambil dari input jika perlu
                    email = email,
                    password = "firebase_managed"
                )
                val newId = database.userDao().insertUser(newUser).toInt()
                localUser = newUser.copy(user_id = newId)
            }

            // 3. Simpan Session
            saveUserSession(localUser.user_id)

            Result.success(localUser)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun logoutUser() {
        firebaseAuth.signOut() // Logout dari Firebase
        prefs.edit().remove("current_user_id").apply() // Hapus session lokal
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

    // Task Methods
    suspend fun getAllTasks(userId: Int): List<Task> {
        return database.taskDao().getAllTasks(userId.toString())
    }

    suspend fun getIncompleteTasks(userId: Int): List<Task> {
        return database.taskDao().getIncompleteTasks(userId.toString())
    }

    suspend fun insertTask(task: Task): Long {
        // Cukup simpan ke lokal.
        // Nanti MainViewModel akan memanggil loadTasks() -> syncTasks() untuk proses upload.
        return database.taskDao().insertTask(task)
    }

    suspend fun updateTask(task: Task) {
        val currentUser = firebaseAuth.currentUser

        // Update Firestore
        if (currentUser != null && task.firestore_id.isNotEmpty()) {
            try {
                firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("tasks")
                    .document(task.firestore_id)
                    .update(
                        mapOf(
                            "title" to task.title,
                            "description" to task.description,
                            "due_date" to task.due_date,
                            "is_completed" to task.isCompleted
                        )
                    ).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Update Room
        database.taskDao().updateTask(task)
    }

    suspend fun deleteTask(task: Task) {
        val currentUser = firebaseAuth.currentUser

        // Hapus dari Firestore
        if (currentUser != null && task.firestore_id.isNotEmpty()) {
            try {
                firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("tasks")
                    .document(task.firestore_id)
                    .delete()
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Hapus dari Room
        database.taskDao().deleteTask(task)
    }

    // Update khusus Checklist
    // Kita ubah parameternya menerima object Task agar bisa ambil firestore_id
    suspend fun toggleTaskCompletion(task: Task, isCompleted: Boolean) {
        val currentUser = firebaseAuth.currentUser

        if (currentUser != null && task.firestore_id.isNotEmpty()) {
            try {
                firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("tasks")
                    .document(task.firestore_id)
                    .update("is_completed", isCompleted)
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Update Room (tetap pakai ID lokal)
        database.taskDao().updateTaskCompletion(task.task_id, isCompleted)
    }

    private suspend fun uploadUnsyncedTasks(userId: Int) {
        val currentUser = firebaseAuth.currentUser ?: return

        // Ambil task lokal yang belum punya ID Firestore
        val unsyncedTasks = database.taskDao().getUnsyncedTasks(userId)

        for (task in unsyncedTasks) {
            try {
                // Buat dokumen baru di Firestore
                val docRef = firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("tasks")
                    .document()

                val firestoreId = docRef.id

                // Siapkan data
                val taskMap = hashMapOf(
                    "firestore_id" to firestoreId,
                    "title" to task.title,
                    "description" to task.description,
                    "due_date" to task.due_date,
                    "is_completed" to task.isCompleted,
                    "created_at" to task.created_at
                )

                // Upload
                docRef.set(taskMap).await()

                // Update lokal dengan ID baru dari Firestore
                val updatedTask = task.copy(firestore_id = firestoreId)
                database.taskDao().updateTask(updatedTask)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun syncTasks(userId: Int) {
        syncMutex.withLock {
            uploadUnsyncedTasks(userId) // Upload dulu

            val currentUser = firebaseAuth.currentUser ?: return@withLock
            try {
                val snapshot = firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("tasks")
                    .get()
                    .await()

                for (document in snapshot.documents) {
                    val firestoreId = document.id
                    val data = document.data ?: continue

                    // 1. Cek apakah data ini SUDAH ADA di lokal?
                    val localTask = database.taskDao().getTaskByFirestoreId(firestoreId)

                    val taskToSave = Task(
                        // 2. KUNCI PENTING:
                        // Jika sudah ada, PAKAI ID LOKAL LAMA agar Room tahu ini update.
                        // Jika belum ada, PAKAI 0 agar Room buat ID baru.
                        task_id = localTask?.task_id ?: 0,

                        user_id = userId,
                        firestore_id = firestoreId,
                        title = data["title"] as? String ?: "",
                        description = data["description"] as? String ?: "",
                        due_date = data["due_date"] as? String ?: "",
                        isCompleted = data["is_completed"] as? Boolean ?: false,
                        created_at = data["created_at"] as? String ?: ""
                    )

                    if (localTask == null) {
                        // Belum ada -> Insert
                        database.taskDao().insertTask(taskToSave)
                    } else {
                        // Sudah ada -> Update
                        database.taskDao().updateTask(taskToSave)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Note Methods
    suspend fun getAllNotes(userId: Int): List<Note> {
        // Untuk performa, kita load dari Room (Offline First).
        // Nanti bisa ditambahkan logika 'sync' terpisah jika perlu.
        return database.noteDao().getAllNotes(userId)
    }

    suspend fun insertNote(note: Note): Long {
        // Cukup simpan ke lokal.
        // MainViewModel akan memanggil loadNotes() -> syncNotes() setelah ini untuk upload.
        return database.noteDao().insertNote(note)
    }

    private suspend fun uploadUnsyncedNotes(userId: Int) {
        val currentUser = firebaseAuth.currentUser ?: return

        // Ambil catatan lokal yang firestore_id-nya masih kosong
        val unsyncedNotes = database.noteDao().getUnsyncedNotes(userId)

        for (note in unsyncedNotes) {
            try {
                // Proses upload sama seperti insert
                val docRef = firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("notes")
                    .document()

                val firestoreId = docRef.id

                val noteMap = hashMapOf(
                    "firestore_id" to firestoreId,
                    "title" to note.title,
                    "content" to note.content,
                    "color_tag" to note.color_tag,
                    "created_at" to note.created_at
                )

                docRef.set(noteMap).await()

                // Update lokal setelah berhasil
                val updatedNote = note.copy(firestore_id = firestoreId)
                database.noteDao().updateNote(updatedNote)

            } catch (e: Exception) {
                e.printStackTrace()
                // Skip jika masih gagal, coba lagi nanti
            }
        }
    }

    suspend fun updateNote(note: Note) {
        val currentUser = firebaseAuth.currentUser

        // Update ke Firestore jika punya ID Firestore
        if (currentUser != null && note.firestore_id.isNotEmpty()) {
            try {
                firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("notes")
                    .document(note.firestore_id)
                    .update(
                        mapOf(
                            "title" to note.title,
                            "content" to note.content,
                            "color_tag" to note.color_tag
                        )
                    ).await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Update ke Room
        database.noteDao().updateNote(note)
    }

    suspend fun deleteNote(note: Note) {
        val currentUser = firebaseAuth.currentUser

        // Hapus dari Firestore jika punya ID Firestore
        if (currentUser != null && note.firestore_id.isNotEmpty()) {
            try {
                firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("notes")
                    .document(note.firestore_id)
                    .delete()
                    .await()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Hapus dari Room
        database.noteDao().deleteNote(note)
    }

    suspend fun syncNotes(userId: Int) {
        // Gunakan withLock agar tidak ada 2 proses sync berjalan bersamaan
        syncMutex.withLock {
            // A. Upload data lokal (Sekarang ini satu-satunya cara upload)
            uploadUnsyncedNotes(userId)

            // B. Download data cloud
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
    suspend fun recoverSessionByEmail(email: String): Boolean {
        return try {
            // 1. Coba cari user di database lokal
            val localUser = database.userDao().getUserByEmail(email)

            if (localUser != null) {
                // KASUS A: Data ada di lokal -> Langsung simpan session
                saveUserSession(localUser.user_id)
                return true
            }

            // 2. KASUS B (Perbaikan): Data lokal hilang, tapi Firebase Login
            // Kita harus "Re-create" user lokal agar sinkron
            val firebaseUser = firebaseAuth.currentUser

            // Pastikan user firebase memang sesuai dengan email yang diminta
            if (firebaseUser != null && firebaseUser.email == email) {
                val newUser = User(
                    username = firebaseUser.displayName ?: "User",
                    email = email,
                    password = "firebase_managed"
                )
                // Masukkan ke Room & ambil ID baru
                val newId = database.userDao().insertUser(newUser).toInt()

                // Simpan session ID baru tersebut
                saveUserSession(newId)

                // Jangan lupa trigger sync data task/note dari cloud karena database baru kosong
                syncTasks(newId)
                syncNotes(newId)

                return true
            }

            // KASUS C: Tidak ada di mana-mana
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
}