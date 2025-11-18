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

// Auth ViewModel
class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TaskifyRepository(AppDatabase.getDatabase(application))

    private val _authState = MutableLiveData<AuthState>()
    val authState: LiveData<AuthState> = _authState

    fun register(email: String, password: String, username: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.registerUser(email, password, username)
            _authState.value = if (result.isSuccess) {
                AuthState.Success(result.getOrNull()!!)
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Registration failed")
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = repository.loginUser(email, password)
            _authState.value = if (result.isSuccess) {
                AuthState.Success(result.getOrNull()!!)
            } else {
                AuthState.Error(result.exceptionOrNull()?.message ?: "Login failed")
            }
        }
    }

    sealed class AuthState {
        object Loading : AuthState()
        data class Success(val user: User) : AuthState()
        data class Error(val message: String) : AuthState()
    }
}
