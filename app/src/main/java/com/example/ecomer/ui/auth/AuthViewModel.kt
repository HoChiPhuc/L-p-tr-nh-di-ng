package com.example.ecomer.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecomer.data.model.SessionState
import com.example.ecomer.data.repository.AuthRepository
import com.example.ecomer.util.ProfileCreationException
import com.example.ecomer.util.toRegisterMessage
import com.example.ecomer.util.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val confirmError: String? = null,
    val isLoading: Boolean = false,
    /** Tài khoản Auth đã tạo, chờ thử lại tạo hồ sơ (khóa form, không tạo Auth trùng). */
    val profilePending: Boolean = false,
    val errorMessage: String? = null
)

/** Quản lý phiên đăng nhập (khôi phục khi mở app) và form đăng nhập. */
class AuthViewModel(private val repository: AuthRepository) : ViewModel() {

    val session: StateFlow<SessionState> = repository.session

    private val _login = MutableStateFlow(LoginUiState())
    val login: StateFlow<LoginUiState> = _login.asStateFlow()

    private val _register = MutableStateFlow(RegisterUiState())
    val register: StateFlow<RegisterUiState> = _register.asStateFlow()

    fun onRegEmailChange(v: String) =
        _register.update { it.copy(email = v, emailError = null, errorMessage = null) }

    fun onRegPasswordChange(v: String) =
        _register.update { it.copy(password = v, passwordError = null, errorMessage = null) }

    fun onRegConfirmChange(v: String) =
        _register.update { it.copy(confirmPassword = v, confirmError = null, errorMessage = null) }

    fun resetRegister() {
        if (_register.value.isLoading) return
        if (_register.value.profilePending) repository.abandonPendingRegistration()
        _register.value = RegisterUiState()
    }

    fun register() {
        val s = _register.value
        if (s.isLoading) return
        if (s.profilePending) {
            _register.update { it.copy(isLoading = true, errorMessage = null) }
            viewModelScope.launch {
                repository.retryProfile()
                    .onSuccess { _register.value = RegisterUiState() }
                    .onFailure { e ->
                        _register.update { it.copy(isLoading = false, errorMessage = e.toRegisterMessage()) }
                    }
            }
            return
        }
        val email = s.email.trim()
        val emailError = when {
            email.isEmpty() -> "Vui lòng nhập email"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Email không hợp lệ"
            else -> null
        }
        val passwordError = when {
            s.password.isEmpty() -> "Vui lòng nhập mật khẩu"
            s.password.length < MIN_PASSWORD -> "Mật khẩu phải có ít nhất $MIN_PASSWORD ký tự"
            else -> null
        }
        val confirmError = when {
            s.confirmPassword.isEmpty() -> "Vui lòng nhập lại mật khẩu"
            s.confirmPassword != s.password -> "Mật khẩu nhập lại không khớp"
            else -> null
        }
        if (emailError != null || passwordError != null || confirmError != null) {
            _register.update {
                it.copy(emailError = emailError, passwordError = passwordError, confirmError = confirmError)
            }
            return
        }
        _register.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.register(email, s.password)
                .onSuccess { _register.value = RegisterUiState() }
                .onFailure { e ->
                    _register.update {
                        it.copy(
                            isLoading = false,
                            profilePending = e is ProfileCreationException,
                            errorMessage = e.toRegisterMessage()
                        )
                    }
                }
        }
    }

    init {
        viewModelScope.launch {
            repository.restoreSession().onFailure { error ->
                val msg = error.toUserMessage()
                Log.w(TAG, "restoreSession onFailure -> UI message: $msg")
                _login.update { it.copy(errorMessage = msg) }
            }
        }
    }

    fun onEmailChange(value: String) =
        _login.update { it.copy(email = value, emailError = null, errorMessage = null) }

    fun onPasswordChange(value: String) =
        _login.update { it.copy(password = value, passwordError = null, errorMessage = null) }

    fun signIn() {
        val state = _login.value
        if (state.isLoading) return
        val email = state.email.trim()
        val emailError = when {
            email.isEmpty() -> "Vui lòng nhập email"
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Email không hợp lệ"
            else -> null
        }
        val passwordError = if (state.password.isEmpty()) "Vui lòng nhập mật khẩu" else null
        if (emailError != null || passwordError != null) {
            _login.update { it.copy(emailError = emailError, passwordError = passwordError) }
            return
        }

        Log.i(TAG, "signIn triggered for email=$email (password hidden)")
        _login.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            repository.signIn(email, state.password)
                .onSuccess {
                    Log.i(TAG, "signIn onSuccess for email=$email")
                    _login.value = LoginUiState(email = email)
                }
                .onFailure { error ->
                    val userMessage = error.toUserMessage()
                    Log.e(TAG, "signIn onFailure for email=$email -> UI display: $userMessage")
                    _login.update {
                        it.copy(isLoading = false, password = "", errorMessage = userMessage)
                    }
                }
        }
    }

    fun signOut() {
        Log.i(TAG, "signOut triggered")
        repository.signOut()
        _login.value = LoginUiState()
    }

    private companion object {
        const val TAG = "ECOMER_AUTH"
        const val MIN_PASSWORD = 6
    }
}
