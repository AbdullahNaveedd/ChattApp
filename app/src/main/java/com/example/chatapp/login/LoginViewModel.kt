package com.example.chatapp.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _loginResult = MutableSharedFlow<Result<String>>()
    val loginResult: SharedFlow<Result<String>> = _loginResult.asSharedFlow()

    fun loginUser(email: String, password: String) {
        viewModelScope.launch {
            authRepository.loginUser(email, password)
                .catch { e -> _loginResult.emit(Result.failure(e)) }
                .collect { result -> _loginResult.emit(result) }
        }
    }
}
