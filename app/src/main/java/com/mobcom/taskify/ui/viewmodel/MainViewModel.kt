package com.mobcom.taskify.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.mobcom.taskify.data.local.AppDatabase
import com.mobcom.taskify.data.model.Note
import com.mobcom.taskify.data.model.Task
import com.mobcom.taskify.data.model.User
import com.mobcom.taskify.data.repository.TaskifyRepository
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TaskifyRepository(AppDatabase.getDatabase(application))

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
                    user_id = userId,
                    title = title,
                    description = description,
                    due_date = dueDate
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