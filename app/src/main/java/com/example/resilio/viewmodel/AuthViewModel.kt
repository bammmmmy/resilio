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
        // Pass credentials as is to repository to support mock accounts like 'user', 'userhead', etc.
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
        if (uid != null) {
            repository.getUserData(uid) { _userState.postValue(it) }
        } else {
            _userState.postValue(null)
        }
    }

    fun logout() {
        repository.logout()
        _userState.postValue(null)
    }
}
