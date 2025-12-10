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

    fun loadTasks() { 
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
//                _tasks.value = repository.getIncompleteTasks(userId)
//
//                try {
//                    repository.syncTasks(userId)
//
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
                val uniqueId = UUID.randomUUID().toString() 

                val task = Task(
                    user_id = userId,
                    firestore_id = uniqueId, 
                    title = title,
                    description = description,
                    due_date = dueDate,
                    isCompleted = false,
                    created_at = System.currentTimeMillis().toString(),
                    is_synced = false,
                    is_deleted = false
                )
                repository.insertTask(task)
            }
        }
    }


    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
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
                if (_currentUserId.value != userId) {
                    _currentUserId.value = userId
                }

                try { repository.syncNotes(userId) } catch (e: Exception) {}
            }
        }
    }

    fun addNote(title: String, content: String, colorTag: String = "#FFFFFF") {
        viewModelScope.launch {
            repository.getCurrentUserId()?.let { userId ->
                val uniqueId = UUID.randomUUID().toString() 
                val note = Note(
                    user_id = userId,
                    firestore_id = uniqueId,
                    title = title,
                    content = content,
                    color_tag = colorTag,
                    is_synced = false
                )
                repository.insertNote(note)

            }
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch {
            repository.updateNote(note)

        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch {
            repository.deleteNote(note)
        }
    }

    fun logout() {
        repository.logoutUser()
    }
    suspend fun recoverSession(email: String): Boolean {
        return repository.recoverSessionByEmail(email)
    }
}