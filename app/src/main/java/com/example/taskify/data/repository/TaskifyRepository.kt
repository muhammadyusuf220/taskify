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
import kotlinx.coroutines.flow.Flow

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
    fun getAllTasks(userId: Int): Flow<List<Task>> {
        return database.taskDao().getAllTasks(userId)
    }

     fun getIncompleteTasks(userId: Int): Flow<List<Task>> {
        return database.taskDao().getIncompleteTasks(userId)
    }

    suspend fun insertTask(task: Task): Long {
        return database.taskDao().insertTask(task)
    }

    suspend fun updateTask(task: Task) {
        val taskToUpdate = task.copy(is_synced = false)
        database.taskDao().updateTask(taskToUpdate) // Update Lokal Cepat

        // Coba Upload Background
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null && task.firestore_id.isNotEmpty()) {
            try {
                firestore.collection("users").document(currentUser.uid)
                    .collection("tasks").document(task.firestore_id)
                    .set(hashMapOf( // Gunakan SET (Upsert)
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
        database.taskDao().updateTask(deletedTask) // Hilang dari UI instan
    }

    // Update khusus Checklist
    // Kita ubah parameternya menerima object Task agar bisa ambil firestore_id
    suspend fun toggleTaskCompletion(task: Task, isCompleted: Boolean) {
        // Update lokal & tandai unsynced
        database.taskDao().updateTaskCompletion(task.task_id, isCompleted)

        // Coba Upload
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
            val currentUser = firebaseAuth.currentUser ?: return@withLock

            // A. UPLOAD NEW / EDITED
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

            // B. UPLOAD DELETED
            val deletedTasks = database.taskDao().getDeletedTasks(userId)
            for (task in deletedTasks) {
                try {
                    if (task.firestore_id.isNotEmpty()) {
                        firestore.collection("users").document(currentUser.uid)
                            .collection("tasks").document(task.firestore_id)
                            .delete().await()
                    }
                    database.taskDao().deleteTask(task) // Hapus permanen lokal
                } catch (e: Exception) { e.printStackTrace() }
            }

            // C. DOWNLOAD DARI SERVER
            try {
                val snapshot = firestore.collection("users")
                    .document(currentUser.uid).collection("tasks").get().await()

                for (doc in snapshot.documents) {
                    val fId = doc.id
                    val data = doc.data ?: continue
                    val localTask = database.taskDao().getTaskByFirestoreId(fId)

                    // PENTING: Jangan timpa jika lokal sedang diedit user (belum sync)
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
                        is_synced = true, // Karena dari server, pasti synced
                        is_deleted = false
                    )

                    if (localTask == null) database.taskDao().insertTask(taskToSave)
                    else database.taskDao().updateTask(taskToSave)
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Note Methods
    fun getAllNotes(userId: Int): Flow<List<Note>> {
        return database.noteDao().getAllNotes(userId)
    }

    suspend fun insertNote(note: Note): Long {
        // Cukup simpan ke lokal.
        // MainViewModel akan memanggil loadNotes() -> syncNotes() setelah ini untuk upload.
        return database.noteDao().insertNote(note)
    }

    private suspend fun uploadUnsyncedNotes(userId: Int) {
        val currentUser = firebaseAuth.currentUser ?: return

        // Query baru: Mengambil yang is_synced = false
        val unsyncedNotes = database.noteDao().getUnsyncedNotes(userId)

        for (note in unsyncedNotes) {
            try {
                // Upload ke Firestore pakai ID yang sudah ada (Upsert)
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
                            // Kita tidak perlu kirim 'is_synced' ke server, itu hanya untuk lokal
                        )
                    ).await()

                // SUKSES? Update status lokal jadi Synced (True)
                database.noteDao().markAsSynced(note.note_id)

            } catch (e: Exception) {
                e.printStackTrace()
                // Jika gagal (internet mati), is_synced tetap false.
                // Nanti akan dicoba lagi saat sync berikutnya.
            }
        }
    }

    suspend fun updateNote(note: Note) {
        // 1. Siapkan data: Update isi note DAN set is_synced = false
        // Ini menandakan data ini "kotor" (dirty) dan perlu diupload lagi nanti
        val noteToUpdate = note.copy(is_synced = false)

        // 2. Update ke Room DULUAN (INSTAN)
        // UI akan langsung berubah karena LiveData mengamati Room
        database.noteDao().updateNote(noteToUpdate)

        // 3. Coba kirim ke Firestore (Background)
        val currentUser = firebaseAuth.currentUser
        if (currentUser != null && note.firestore_id.isNotEmpty()) {
            try {
                firestore.collection("users")
                    .document(currentUser.uid)
                    .collection("notes")
                    .document(note.firestore_id)
                    .set( // Gunakan SET (Upsert), bukan UPDATE, agar konsisten
                        hashMapOf(
                            "firestore_id" to note.firestore_id,
                            "title" to note.title,
                            "content" to note.content,
                            "color_tag" to note.color_tag,
                            "created_at" to note.created_at
                        )
                    ).await()

                // 4. Jika internet lancar & sukses upload -> Set is_synced = true kembali
                database.noteDao().markAsSynced(note.note_id)

            } catch (e: Exception) {
                e.printStackTrace()
                // Jika gagal (Offline), tidak apa-apa.
                // Data di Room tetap 'is_synced = false',
                // jadi akan terambil otomatis saat fungsi 'syncNotes()' berjalan nanti.
            }
        }
    }

    suspend fun deleteNote(note: Note) {
        // 1. Update status jadi deleted & belum sync
        val deletedNote = note.copy(
            is_deleted = true,
            is_synced = false
        )

        // 2. Simpan perubahan ke Room (INSTAN, UI langsung update karena terfilter di getAllNotes)
        database.noteDao().updateNote(deletedNote)

        // 3. Coba sync jika ada internet (Opsional di sini, bisa serahkan ke Worker)
        // uploadDeletedNotes(note.user_id)
    }

    suspend fun syncNotes(userId: Int) {
        // Gunakan withLock agar tidak ada 2 proses sync berjalan bersamaan
        syncMutex.withLock {
            // A. Upload data lokal (Sekarang ini satu-satunya cara upload)
            uploadUnsyncedNotes(userId)

            uploadDeletedNotes(userId)

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

    private suspend fun uploadDeletedNotes(userId: Int) {
        val currentUser = firebaseAuth.currentUser ?: return

        // 1. Ambil data yang statusnya "deleted" dari Room
        val deletedNotes = database.noteDao().getDeletedNotes(userId)

        for (note in deletedNotes) {
            try {
                // 2. Hapus dari Firestore
                if (note.firestore_id.isNotEmpty()) {
                    firestore.collection("users")
                        .document(currentUser.uid)
                        .collection("notes")
                        .document(note.firestore_id)
                        .delete()
                        .await()
                }

                // 3. Hapus PERMANEN dari Room setelah sukses di cloud
                database.noteDao().deleteNote(note)

            } catch (e: Exception) {
                e.printStackTrace()
                // Jika gagal, data tetap berstatus 'is_deleted = true' di Room.
                // User tetap tidak melihatnya, dan akan dicoba hapus lagi nanti.
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