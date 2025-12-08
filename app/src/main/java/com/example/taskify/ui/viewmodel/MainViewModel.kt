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
import com.example.taskify.data.model.Holiday
import com.example.taskify.data.repository.TaskifyRepository
import kotlinx.coroutines.launch
import androidx.lifecycle.*
import java.util.UUID

// Main ViewModel
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TaskifyRepository.getInstance(
        AppDatabase.getDatabase(application),
        application
    )

    private val _tasks = MutableLiveData<List<Task>>()


    private val _notes = MutableLiveData<List<Note>>()

    val holidays: LiveData<List<Holiday>> = repository.getHolidays().asLiveData()

    private val _currentUserId = MutableLiveData<Int>()

    init {
        // Panggil fungsi sync saat ViewModel dibuat
        refreshHolidays()
    }

    fun refreshHolidays() {
        viewModelScope.launch {
            repository.syncHolidays()
        }
    }

    val notes: LiveData<List<Note>> = _currentUserId.switchMap { userId ->
        repository.getAllNotes(userId).asLiveData()
    }

    val tasks: LiveData<List<Task>> = _currentUserId.switchMap { userId ->
        repository.getAllTasks(userId).asLiveData()
    }

    fun loadTasks() { // Bisa diganti nama jadi refreshTasks
        viewModelScope.launch {
            repository.getCurrentUserId()?.let { userId ->
                if (_currentUserId.value != userId) _currentUserId.value = userId
                try { repository.syncTasks(userId) } catch (e: Exception) {}
            }
        }
    }

//    fun loadIncompleteTasks() {
//        viewModelScope.launch {
//            repository.getCurrentUserId()?.let { userId ->
//                // 1. TAMPILKAN DATA LOKAL DULUAN
//                _tasks.value = repository.getIncompleteTasks(userId)
//
//                // 2. Sync di Background
//                try {
//                    repository.syncTasks(userId)
//
//                    // 3. Refresh lagi
//                    _tasks.value = repository.getIncompleteTasks(userId)
//                } catch (e: Exception) {
//                    e.printStackTrace()
//                }
//            }
//        }
//    }

    fun addTask(title: String, description: String, dueDate: String) {
        viewModelScope.launch {
            repository.getCurrentUserId()?.let { userId ->
                val uniqueId = UUID.randomUUID().toString() // Generate Client ID

                val task = Task(
                    user_id = userId,
                    firestore_id = uniqueId, // Langsung isi!
                    title = title,
                    description = description,
                    due_date = dueDate,
                    isCompleted = false,
                    created_at = System.currentTimeMillis().toString(),
                    is_synced = false,
                    is_deleted = false
                )
                repository.insertTask(task)

                // TIDAK PERLU loadTasks(), Flow otomatis update UI
            }
        }
    }


    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
            // TIDAK PERLU loadTasks()
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
        }
    }

    fun toggleTaskCompletion(task: Task, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.toggleTaskCompletion(task, isCompleted)
        }
    }

    fun loadNotes() {
        viewModelScope.launch {
            repository.getCurrentUserId()?.let { userId ->
                // Trigger Flow agar UI update
                if (_currentUserId.value != userId) {
                    _currentUserId.value = userId
                }

                // Jalanin sync diam-diam di background
                try { repository.syncNotes(userId) } catch (e: Exception) {}
            }
        }
    }

    fun addNote(title: String, content: String, colorTag: String = "#FFFFFF") {
        viewModelScope.launch {
            repository.getCurrentUserId()?.let { userId ->
                val uniqueId = UUID.randomUUID().toString() // UUID aman sekarang
                val note = Note(
                    user_id = userId,
                    firestore_id = uniqueId,
                    title = title,
                    content = content,
                    color_tag = colorTag,
                    is_synced = false
                )
                repository.insertNote(note)

                // HAPUS: loadNotes() <-- TIDAK PERLU LAGI
                // Room otomatis tahu ada data baru dan update 'val notes' di atas
            }
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note)

            // HAPUS: loadNotes() <-- TIDAK PERLU LAGI
            // UI akan update sendiri sepersekian detik setelah baris di atas selesai
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
            // HAPUS: loadNotes() <-- TIDAK PERLU LAGI
        }
    }

    fun logout() {
        repository.logoutUser()
    }
    suspend fun recoverSession(email: String): Boolean {
        return repository.recoverSessionByEmail(email)
    }
}