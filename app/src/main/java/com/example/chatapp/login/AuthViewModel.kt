package com.example.chatapp.login

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _signupStatus = MutableLiveData<Result<String>>()
    val signupStatus: LiveData<Result<String>> = _signupStatus

    private val _loginStatus = MutableLiveData<Result<String>>()
    val loginStatus: LiveData<Result<String>> = _loginStatus

    fun registerUser(name: String, email: String, password: String, confirmPassword: String) {
        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            _signupStatus.value = Result.failure(Exception("Please fill all fields"))
            return
        }

        if (password != confirmPassword) {
            _signupStatus.value = Result.failure(Exception("Passwords do not match"))
            return
        }

        viewModelScope.launch {
            authRepository.registerUser(name, email, password)
                .catch { e -> _signupStatus.postValue(Result.failure(e)) }
                .collect { result -> _signupStatus.postValue(result) }
        }
    }

    fun loginUser(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            _loginStatus.value = Result.failure(Exception("Email and password required"))
            return
        }


        viewModelScope.launch {
            authRepository.loginUser(email, password)
                .catch { e -> _loginStatus.postValue(Result.failure(e)) }
                .collect { result -> _loginStatus.postValue(result) }
        }
    }


}
