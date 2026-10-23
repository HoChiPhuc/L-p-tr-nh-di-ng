package com.example.ecomer.data.model

/** Vai trò lưu ở users/{uid}.role. Chỉ chấp nhận đúng "admin" hoặc "user". */
enum class UserRole(val value: String) {
    ADMIN("admin"),
    USER("user");

    companion object {
        const val COLLECTION = "users"
        const val FIELD_ROLE = "role"

        /** Trả về null nếu giá trị không hợp lệ (không bao giờ mặc định thành admin). */
        fun fromValue(value: String?): UserRole? = entries.firstOrNull { it.value == value }
    }
}

/** Người dùng đã đăng nhập và đã xác định được vai trò hợp lệ. */
data class AppUser(
    val uid: String,
    val email: String,
    val role: UserRole
) {
    val isAdmin: Boolean get() = role == UserRole.ADMIN
}

/** Trạng thái phiên đăng nhập của ứng dụng. */
sealed interface SessionState {
    /** Đang kiểm tra phiên đăng nhập khi mở app. */
    data object Loading : SessionState

    data object SignedOut : SessionState

    data class SignedIn(val user: AppUser) : SessionState
}
