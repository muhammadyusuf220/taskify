package com.example.taskify.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.taskify.data.local.AppDatabase
import com.example.taskify.data.model.Note
import com.example.taskify.data.model.Task
import com.example.taskify.data.model.User
import com.example.taskify.data.repository.TaskifyRepository
import kotlinx.coroutines.launch

// Main ViewModel
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TaskifyRepository.getInstance(
        AppDatabase.getDatabase(application),
        application
    )

    private val _tasks = MutableLiveData<List<Task>>()
    val tasks: LiveData<List<Task>> = _tasks

    private val _notes = MutableLiveData<List<Note>>()
    val notes: LiveData<List<Note>> = _notes

    fun loadTasks() {
        viewModelScope.launch {
            repository.getCurrentUserId()?.let { userId ->
                // 1. TAMPILKAN DATA LOKAL DULUAN (Instan, Offline jalan)
                _tasks.value = repository.getAllTasks(userId)

                // 2. Baru coba Sync ke Internet (Background)
                try {
                    repository.syncTasks(userId)

                    // 3. Refresh lagi setelah sync (jika ada update dari cloud)
                    _tasks.value = repository.getAllTasks(userId)
                } catch (e: Exception) {
                    // Jika internet mati, user tidak terganggu karena data lokal sudah tampil
                    e.printStackTrace()
                }
            }
        }
    }

    fun loadIncompleteTasks() {
        viewModelScope.launch {
            repository.getCurrentUserId()?.let { userId ->
                // 1. TAMPILKAN DATA LOKAL DULUAN
                _tasks.value = repository.getIncompleteTasks(userId)

                // 2. Sync di Background
                try {
                    repository.syncTasks(userId)

                    // 3. Refresh lagi
                    _tasks.value = repository.getIncompleteTasks(userId)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun addTask(title: String, description: String, dueDate: String) {
        viewModelScope.launch {
            repository.getCurrentUserId()?.let { userId ->
                val task = Task(
                    task_id = 0,
                    user_id = userId,
                    firestore_id = "", // Default kosong, Repository yang isi
                    title = title,
                    description = description,
                    due_date = dueDate,
                    isCompleted = false,
                    created_at = System.currentTimeMillis().toString()
                )
                repository.insertTask(task)
                loadTasks() // atau loadIncompleteTasks() tergantung halaman
            }
        }
    }


    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
            loadTasks()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            loadTasks()
        }
    }

    fun toggleTaskCompletion(task: Task, isCompleted: Boolean) {
        viewModelScope.launch {
            // Kirim object task ke repository agar bisa baca firestore_id
            repository.toggleTaskCompletion(task, isCompleted)
            loadTasks()
        }
    }

    fun loadNotes() {
        viewModelScope.launch {
            repository.getCurrentUserId()?.let { userId ->
                // PERUBAHAN 1: Load Lokal (Langsung Tampil)
                _notes.value = repository.getAllNotes(userId)

                // PERUBAHAN 2: Sync Cloud (Belakangan)
                try {
                    repository.syncNotes(userId)
                    // Refresh lagi untuk memastikan data terbaru
                    _notes.value = repository.getAllNotes(userId)
                } catch (e: Exception) {
                    // Internet mati? Tidak masalah.
                    e.printStackTrace()
                }
            }
        }
    }

    fun addNote(title: String, content: String, colorTag: String = "#FFFFFF") {
        viewModelScope.launch {
            repository.getCurrentUserId()?.let { userId ->
                val note = Note(
                    user_id = userId,
                    firestore_id = "", // Kosongkan saat inisialisasi, Repository yang akan isi
                    title = title,
                    content = content,
                    color_tag = colorTag
                )
                repository.insertNote(note)
                loadNotes()
            }
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note)
            loadNotes()
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
            loadNotes()
        }
    }

    fun logout() {
        repository.logoutUser()
    }
    suspend fun recoverSession(email: String): Boolean {
        return repository.recoverSessionByEmail(email)
    }
}