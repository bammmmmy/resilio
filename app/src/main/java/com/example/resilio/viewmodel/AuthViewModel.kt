package com.example.resilio.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.resilio.model.User
import com.example.resilio.repository.AuthRepository

class AuthViewModel : ViewModel() {
    private val repository = AuthRepository()

    private val _userState = MutableLiveData<Result<User>?>()
    val userState: LiveData<Result<User>?> = _userState

    fun login(email: String, pass: String) {
        repository.login(email, pass) { result ->
            _userState.postValue(result)
        }
    }

    fun register(user: User, pass: String) {
        repository.register(user, pass) { result ->
            _userState.postValue(result)
        }
    }

    fun checkAuthState() {
        val uid = repository.getCurrentUserUid()
        if (uid == null) {
            _userState.postValue(null)
            return
        }

        val currentUser = repository.getCurrentFirebaseUser()
        repository.getUserData(uid) { result ->
            result.onSuccess { user ->
                val isAdmin = user.role == com.example.resilio.model.UserRole.BDRRMO || user.role == com.example.resilio.model.UserRole.CHAIRMAN
                if (currentUser != null && !isAdmin && !currentUser.isEmailVerified) {
                    repository.logout()
                    _userState.postValue(Result.failure(Exception("Please verify your email before logging in.")))
                    return@getUserData
                }
                _userState.postValue(Result.success(user))
            }.onFailure {
                _userState.postValue(result)
            }
        }
    }

    fun logout() {
        repository.logout()
        _userState.postValue(null)
    }

    fun updateProfile(user: User, onResult: (Result<Unit>) -> Unit) {
        repository.updateProfile(user, onResult)
    }

    fun resetPassword(email: String, onResult: (Result<Unit>) -> Unit) {
        repository.resetPassword(email, onResult)
    }

    fun uploadProfileImage(uri: android.net.Uri, onResult: (Result<String>) -> Unit) {
        repository.uploadProfileImage(uri, onResult)
    }
}
