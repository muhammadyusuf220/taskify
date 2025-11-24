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
    private val repository = TaskifyRepository(
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
                _tasks.value = repository.getAllTasks(userId)
            }
        }
    }

    fun loadIncompleteTasks() {
        viewModelScope.launch {
            repository.getCurrentUserId()?.let { userId ->
                _tasks.value = repository.getIncompleteTasks(userId)
            }
        }
    }

    fun addTask(title: String, description: String, dueDate: String) {
        viewModelScope.launch {
            repository.getCurrentUserId()?.let { userId ->
                val task = Task(
                    task_id = 0,                     // autoGenerate
                    user_id = userId,
                    title = title,
                    description = description,
                    due_date = dueDate,
                    isCompleted = false,             // default awal
                    created_at = System.currentTimeMillis().toString()
                )
                repository.insertTask(task)
                loadTasks()
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

    fun toggleTaskCompletion(taskId: Int, isCompleted: Boolean) {
        viewModelScope.launch {
            repository.toggleTaskCompletion(taskId, isCompleted)
            loadTasks()
        }
    }

    fun loadNotes() {
        viewModelScope.launch {
            repository.getCurrentUserId()?.let { userId ->
                _notes.value = repository.getAllNotes(userId)
            }
        }
    }

    fun addNote(title: String, content: String, colorTag: String = "#FFFFFF") {
        viewModelScope.launch {
            repository.getCurrentUserId()?.let { userId ->
                val note = Note(
                    user_id = userId,
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
}