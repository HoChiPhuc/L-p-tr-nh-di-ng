package com.example.ecomer.data.repository

import android.util.Log
import com.example.ecomer.data.model.AppUser
import com.example.ecomer.data.model.SessionState
import com.example.ecomer.data.model.UserRole
import com.example.ecomer.util.AppException
import com.example.ecomer.util.ProfileCreationException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * Xác thực bằng Firebase Auth (email/password) và đọc vai trò từ Firestore users/{uid}.role.
 * Vai trò KHÔNG bao giờ được tự cấp: thiếu document hoặc role sai => đăng xuất.
 * Ghi logcat có kiểm soát bằng tag ECOMER_AUTH (tuyệt đối không log mật khẩu/token).
 */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    private val _session = MutableStateFlow<SessionState>(SessionState.Loading)
    val session: StateFlow<SessionState> = _session.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null && _session.value is SessionState.SignedIn) {
                Log.i(TAG, "AuthStateListener: Phiên đăng nhập kết thúc, đưa về SignedOut")
                _session.value = SessionState.SignedOut
            }
        }
    }

    /** Khôi phục phiên khi mở lại app. Lỗi => đăng xuất an toàn và trả về nguyên nhân. */
    suspend fun restoreSession(): Result<Unit> {
        val current = auth.currentUser
        if (current == null) {
            Log.d(TAG, "restoreSession: Chưa có người dùng đăng nhập")
            _session.value = SessionState.SignedOut
            return Result.success(Unit)
        }
        Log.i(TAG, "restoreSession: Khôi phục phiên cho email=${current.email}, uid=${current.uid}")
        return try {
            val appUser = resolveUser(current)
            _session.value = SessionState.SignedIn(appUser)
            Log.i(TAG, "restoreSession: Thành công, vai trò=${appUser.role.value}")
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(
                TAG,
                "restoreSession thất bại: errorClass=${e.javaClass.name}, " +
                    "errorCode=${(e as? FirebaseAuthException)?.errorCode ?: (e as? FirebaseFirestoreException)?.code?.name}, " +
                    "message=${e.message}"
            )
            auth.signOut()
            _session.value = SessionState.SignedOut
            Result.failure(e)
        }
    }

    suspend fun signIn(email: String, password: String): Result<AppUser> {
        val trimmedEmail = email.trim()
        Log.i(TAG, "signIn: Bắt đầu đăng nhập bằng Email/Password cho tài khoản: $trimmedEmail")
        return try {
            val authResult = auth.signInWithEmailAndPassword(trimmedEmail, password).await()
            val firebaseUser = authResult.user
                ?: throw AppException("Không lấy được thông tin người dùng từ kết quả đăng nhập.")

            Log.i(TAG, "signIn: Firebase Auth thành công, uid=${firebaseUser.uid}. Đang xác minh quyền trong Firestore...")

            val appUser = try {
                resolveUser(firebaseUser)
            } catch (e: Exception) {
                Log.w(TAG, "signIn: Xác định quyền thất bại sau khi auth thành công -> đăng xuất an toàn. Lỗi: ${e.message}")
                auth.signOut()
                throw e
            }
            _session.value = SessionState.SignedIn(appUser)
            Log.i(TAG, "signIn: Hoàn tất đăng nhập thành công cho uid=${appUser.uid}, role=${appUser.role.value}")
            Result.success(appUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val errorCode = (e as? FirebaseAuthException)?.errorCode
            Log.e(
                TAG,
                "signIn: Đăng nhập THẤT BẠI cho email=$trimmedEmail. " +
                    "errorClass=${e.javaClass.name}, errorCode=$errorCode, message=${e.message}",
                e
            )
            Result.failure(e)
        }
    }

    /**
     * Đăng ký: tạo tài khoản Auth rồi tạo users/{uid} = {role:"user"}.
     * Nếu ghi Firestore thất bại, tài khoản Auth được giữ nguyên (đang đăng nhập)
     * và ném [ProfileCreationException] để UI gọi [retryProfile] mà không tạo Auth trùng.
     * Không bao giờ log mật khẩu.
     */
    suspend fun register(email: String, password: String): Result<AppUser> {
        val trimmedEmail = email.trim()
        return try {
            val authResult = auth.createUserWithEmailAndPassword(trimmedEmail, password).await()
            val firebaseUser = authResult.user
                ?: throw AppException("Không lấy được thông tin người dùng sau khi đăng ký.")
            Log.i(TAG, "register: Auth tạo thành công uid=${firebaseUser.uid}")
            finishProfile(firebaseUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "register thất bại: class=${e.javaClass.name}, code=${(e as? FirebaseAuthException)?.errorCode}")
            Result.failure(e)
        }
    }

    /** Thử lại tạo hồ sơ cho tài khoản Auth đã tạo nhưng chưa có hồ sơ (không tạo tài khoản mới). */
    suspend fun retryProfile(): Result<AppUser> {
        val current = auth.currentUser
            ?: return Result.failure(AppException("Phiên đăng ký đã hết hạn. Vui lòng đăng nhập lại."))
        return try {
            finishProfile(current)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Hủy phiên đăng ký dang dở (tài khoản Auth vẫn còn, lần sau đăng nhập sẽ tự khởi tạo hồ sơ user). */
    fun abandonPendingRegistration() {
        auth.signOut()
        _session.value = SessionState.SignedOut
    }

    private suspend fun finishProfile(firebaseUser: FirebaseUser): Result<AppUser> {
        return try {
            val appUser = resolveUser(firebaseUser)
            _session.value = SessionState.SignedIn(appUser)
            Result.success(appUser)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "finishProfile thất bại: class=${e.javaClass.name}")
            Result.failure(ProfileCreationException(e))
        }
    }

    fun signOut() {
        Log.i(TAG, "signOut: Người dùng đăng xuất")
        auth.signOut()
        _session.value = SessionState.SignedOut
    }

    private suspend fun resolveUser(firebaseUser: FirebaseUser): AppUser {
        Log.d(TAG, "resolveUser: Đang đọc document users/${firebaseUser.uid} trên Firestore")
        val snapshot = try {
            withTimeout(REQUEST_TIMEOUT_MS) {
                firestore.collection(UserRole.COLLECTION).document(firebaseUser.uid).get().await()
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "resolveUser: Hết thời gian chờ đọc Firestore users/${firebaseUser.uid}")
            throw AppException("Không đọc được vai trò tài khoản (hết thời gian chờ). Kiểm tra mạng và thử lại.")
        } catch (e: FirebaseFirestoreException) {
            Log.e(TAG, "resolveUser: Lỗi Firestore [${e.code}]: ${e.message}", e)
            throw e
        }

        val roleString: String? = if (!snapshot.exists()) {
            // Chỉ khởi tạo role=user (rules chỉ cho phép create với role=user, không ghi đè được).
            Log.w(TAG, "resolveUser: Chưa có users/${firebaseUser.uid}, khởi tạo role=user")
            try {
                withTimeout(REQUEST_TIMEOUT_MS) {
                    firestore.collection(UserRole.COLLECTION).document(firebaseUser.uid)
                        .set(mapOf(UserRole.FIELD_ROLE to UserRole.USER.value)).await()
                }
            } catch (e: TimeoutCancellationException) {
                throw AppException("Không tạo được hồ sơ phân quyền (hết thời gian chờ). Kiểm tra mạng và thử lại.")
            }
            UserRole.USER.value
        } else {
            snapshot.getString(UserRole.FIELD_ROLE)
        }
        Log.d(TAG, "resolveUser: Tìm thấy document, role raw value='$roleString'")

        val role = UserRole.fromValue(roleString)
            ?: throw AppException(
                "Vai trò tài khoản không hợp lệ (nhận được: '$roleString', chỉ chấp nhận 'admin' hoặc 'user')."
            )

        return AppUser(uid = firebaseUser.uid, email = firebaseUser.email.orEmpty(), role = role)
    }

    private companion object {
        const val TAG = "ECOMER_AUTH"
        const val REQUEST_TIMEOUT_MS = 20_000L
    }
}
