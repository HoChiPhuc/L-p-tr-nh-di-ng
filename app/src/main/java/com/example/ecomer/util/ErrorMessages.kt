package com.example.ecomer.util

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.TimeoutCancellationException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Lỗi nghiệp vụ có sẵn thông báo tiếng Việt hiển thị cho người dùng. */
open class AppException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Tài khoản Auth đã tạo nhưng hồ sơ phân quyền Firestore chưa xử lý được (có thể thử lại an toàn). */
class ProfileCreationException(cause: Throwable) :
    AppException(
        "Tài khoản đã được tạo nhưng chưa lưu được hồ sơ phân quyền: ${cause.toUserMessage()} " +
            "Nhấn \"THỬ LẠI\" để hoàn tất (sẽ không tạo tài khoản trùng).",
        cause
    )

/** Thông báo riêng cho luồng đăng ký (phân biệt email trùng, email sai, mật khẩu yếu, mạng, Firestore). */
fun Throwable.toRegisterMessage(): String = when (this) {
    is ProfileCreationException -> message ?: "Lỗi tạo hồ sơ phân quyền."
    is FirebaseAuthUserCollisionException -> "Email này đã được sử dụng. Vui lòng đăng nhập hoặc dùng email khác."
    is FirebaseAuthWeakPasswordException -> "Mật khẩu quá yếu: ${reason ?: "cần tối thiểu 6 ký tự"}."
    is FirebaseAuthInvalidCredentialsException -> "Email không hợp lệ."
    is FirebaseNetworkException -> "Lỗi mạng: không thể kết nối tới máy chủ. Kiểm tra Internet và thử lại."
    else -> toUserMessage()
}

/**
 * Phân tích và chuyển đổi chi tiết các ngoại lệ hệ thống / Firebase / Cloudinary
 * thành thông báo cụ thể, có mã lỗi kỹ thuật rõ ràng, không nuốt lỗi hay gộp chung.
 */
fun Throwable.toUserMessage(): String = when (this) {
    is AppException ->
        message ?: "Lỗi nghiệp vụ không xác định."

    // 1. Phân biệt tài khoản không tồn tại
    is FirebaseAuthInvalidUserException -> {
        val code = errorCode.ifBlank { "USER_NOT_FOUND" }
        "Tài khoản không tồn tại trên Firebase Auth [Mã: $code]. " +
            "Vui lòng tạo tài khoản này trong Firebase Console -> Authentication -> Users."
    }

    // 2. Phân biệt sai thông tin xác thực / sai mật khẩu
    is FirebaseAuthInvalidCredentialsException -> {
        val code = errorCode.ifBlank { "INVALID_CREDENTIALS" }
        "Email hoặc mật khẩu không chính xác [Mã: $code]. " +
            "Lưu ý: Mật khẩu này là mật khẩu tài khoản được tạo trên Firebase Auth, KHÔNG PHẢI mật khẩu hòm thư Gmail cá nhân."
    }

    // 3. Email đã tồn tại (dùng cho luồng đăng ký nếu có)
    is FirebaseAuthUserCollisionException -> {
        val code = errorCode.ifBlank { "EMAIL_COLLISION" }
        "Email này đã được sử dụng bởi một tài khoản khác [Mã: $code]."
    }

    // 4. Các lỗi FirebaseAuthException khác dựa trên errorCode chuẩn
    is FirebaseAuthException -> when (errorCode) {
        "ERROR_OPERATION_NOT_ALLOWED", "OPERATION_NOT_ALLOWED" ->
            "Phương thức đăng nhập Email/Password CHƯA ĐƯỢC BẬT trong Firebase Console -> Authentication -> Sign-in method."
        "ERROR_USER_DISABLED", "USER_DISABLED" ->
            "Tài khoản này đã bị khóa hoặc vô hiệu hóa trong Firebase Console [Mã: $errorCode]."
        "ERROR_USER_NOT_FOUND", "USER_NOT_FOUND" ->
            "Không tìm thấy tài khoản tương ứng với email đã nhập [Mã: $errorCode]."
        "ERROR_WRONG_PASSWORD", "WRONG_PASSWORD" ->
            "Mật khẩu không chính xác [Mã: $errorCode]."
        "ERROR_TOO_MANY_REQUESTS", "TOO_MANY_REQUESTS" ->
            "Đăng nhập sai quá nhiều lần. Thiết bị tạm thời bị giới hạn, vui lòng thử lại sau ít phút [Mã: $errorCode]."
        "ERROR_INVALID_EMAIL", "INVALID_EMAIL" ->
            "Định dạng địa chỉ email không hợp lệ [Mã: $errorCode]."
        "ERROR_APP_NOT_AUTHORIZED", "APP_NOT_AUTHORIZED" ->
            "Ứng dụng chưa được cấp quyền gọi Firebase Auth (kiểm tra SHA-1 fingerprint trong Firebase Console) [Mã: $errorCode]."
        "ERROR_API_KEY_NOT_VALID", "API_KEY_NOT_VALID" ->
            "API Key trong google-services.json không hợp lệ hoặc bị giới hạn trên Google Cloud Console."
        else ->
            "Lỗi xác thực Firebase [${errorCode.ifBlank { this::class.java.simpleName }}]: ${localizedMessage ?: message ?: "Vui lòng thử lại."}"
    }

    // 5. Lỗi kết nối mạng từ Firebase SDK (thường do timeout kết nối máy chủ Google hoặc reCAPTCHA verification)
    is FirebaseNetworkException -> {
        val detail = localizedMessage ?: message ?: "Không có thông tin chi tiết"
        "Lỗi mạng Firebase (FirebaseNetworkException): Thiết bị/máy ảo không thể kết nối tới máy chủ Google Identity Toolkit / reCAPTCHA. " +
            "[Chi tiết: $detail]. Vui lòng kiểm tra kết nối mạng của máy ảo hoặc cấu hình SHA-1 trong Firebase Console."
    }

    // 6. Quá nhiều yêu cầu cấp Firebase
    is FirebaseTooManyRequestsException ->
        "Máy chủ Firebase tạm thời quá tải hoặc giới hạn tần suất yêu cầu. Vui lòng thử lại sau ít phút."

    // 7. Lỗi Cloud Firestore phân quyền hoặc mất kết nối
    is FirebaseFirestoreException -> when (code) {
        FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Lỗi phân quyền Firestore (PERMISSION_DENIED): Tài khoản đã xác thực nhưng bị từ chối truy cập document 'users/{uid}'. " +
                "Vui lòng kiểm tra Firestore Security Rules và document vai trò của user."
        FirebaseFirestoreException.Code.UNAVAILABLE ->
            "Không thể kết nối máy chủ Cloud Firestore (UNAVAILABLE). Kiểm tra kết nối Internet trên thiết bị."
        FirebaseFirestoreException.Code.NOT_FOUND ->
            "Không tìm thấy tài liệu trên Firestore (NOT_FOUND)."
        FirebaseFirestoreException.Code.UNAUTHENTICATED ->
            "Phiên đăng nhập không hợp lệ hoặc token đã hết hạn (UNAUTHENTICATED)."
        FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
            "Máy chủ Cloud Firestore phản hồi quá thời gian chờ (DEADLINE_EXCEEDED)."
        else ->
            "Lỗi Cloud Firestore [${code.name}]: ${localizedMessage ?: message ?: ""}"
    }

    // 8. Lỗi I/O và Timeout
    is SocketTimeoutException, is TimeoutCancellationException ->
        "Hết thời gian chờ phản hồi từ máy chủ (Timeout). Vui lòng thử lại."
    is UnknownHostException ->
        "Không thể phân giải tên miền máy chủ (UnknownHostException). Vui lòng kiểm tra DNS hoặc kết nối mạng."
    is IOException ->
        "Lỗi I/O truyền nhận dữ liệu [${this::class.java.simpleName}]: ${localizedMessage ?: message ?: ""}"

    // 9. Ngoại lệ chưa xác định khác
    else ->
        "Lỗi không xác định [${this::class.java.name}]: ${localizedMessage ?: message ?: "Vui lòng thử lại."}"
}
