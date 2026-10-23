package com.example.ecomer.data.remote

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.ecomer.util.AppException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Tải ảnh lên Cloudinary qua REST API multipart/form-data.
 * Sử dụng unsigned upload preset, tuyệt đối KHÔNG chứa API Secret trên client Android.
 */
object CloudinaryUploader {
    const val CLOUD_NAME = "px1hnu6s"
    const val UPLOAD_PRESET = "ecomer_unsigned"
    const val UPLOAD_ENDPOINT = "https://api.cloudinary.com/v1_1/$CLOUD_NAME/image/upload"

    private const val MAX_IMAGE_BYTES = 10 * 1024 * 1024 // 10 MB

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Tải tệp ảnh từ [uri] lên Cloudinary và trả về [secure_url].
     * @throws AppException khi có lỗi mạng, lỗi máy chủ, JSON không hợp lệ hoặc dữ liệu không đúng.
     */
    suspend fun uploadImage(uri: Uri, contentResolver: ContentResolver): String = withContext(Dispatchers.IO) {
        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
        if (!mimeType.startsWith("image/")) {
            throw AppException("Tệp đã chọn không phải là định dạng hình ảnh hợp lệ ($mimeType).")
        }

        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType) ?: "jpg"
        val fileName = "upload_${UUID.randomUUID()}.$extension"

        val bytes = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes()
            } ?: throw AppException("Không thể đọc tệp ảnh từ thiết bị.")
        } catch (e: Exception) {
            if (e is AppException) throw e
            throw AppException("Lỗi khi đọc tệp ảnh: ${e.message}", e)
        }

        if (bytes.isEmpty()) {
            throw AppException("Tệp ảnh rỗng, vui lòng chọn lại.")
        }

        if (bytes.size > MAX_IMAGE_BYTES) {
            throw AppException("Kích thước ảnh vượt quá giới hạn cho phép (tối đa 10MB).")
        }

        val mediaType = mimeType.toMediaTypeOrNull()

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("upload_preset", UPLOAD_PRESET)
            .addFormDataPart(
                "file",
                fileName,
                bytes.toRequestBody(mediaType)
            )
            .build()

        val request = Request.Builder()
            .url(UPLOAD_ENDPOINT)
            .post(requestBody)
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw AppException("Tải ảnh lên Cloudinary quá thời gian chờ (timeout). Vui lòng kiểm tra mạng và thử lại.", e)
        } catch (e: UnknownHostException) {
            throw AppException("Không thể kết nối tới Cloudinary (lỗi phân giải tên miền). Vui lòng kiểm tra Internet.", e)
        } catch (e: IOException) {
            throw AppException("Lỗi kết nối khi tải ảnh lên Cloudinary: ${e.localizedMessage ?: e.message}", e)
        }

        response.use { resp ->
            val responseBody = resp.body?.string().orEmpty()

            if (!resp.isSuccessful) {
                val detailError = parseCloudinaryErrorMessage(responseBody)
                throw AppException(
                    detailError ?: "Tải ảnh lên Cloudinary thất bại (mã phản hồi HTTP ${resp.code}: ${resp.message})."
                )
            }

            if (responseBody.isBlank()) {
                throw AppException("Máy chủ Cloudinary trả về phản hồi rỗng.")
            }

            try {
                val json = JSONObject(responseBody)
                val secureUrl = json.optString("secure_url").trim()
                val fallbackUrl = json.optString("url").trim()
                val finalUrl = when {
                    secureUrl.isNotEmpty() -> secureUrl
                    fallbackUrl.isNotEmpty() -> fallbackUrl
                    else -> throw AppException("Dữ liệu phản hồi từ Cloudinary không chứa URL ảnh (secure_url/url).")
                }
                finalUrl
            } catch (e: JSONException) {
                throw AppException("Không thể phân tích dữ liệu phản hồi từ Cloudinary (JSON không hợp lệ).", e)
            }
        }
    }

    private fun parseCloudinaryErrorMessage(responseBody: String): String? {
        if (responseBody.isBlank()) return null
        return try {
            val json = JSONObject(responseBody)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message") ?: json.optString("message")
            if (message.isNotBlank()) "Cloudinary: $message" else null
        } catch (_: Exception) {
            null
        }
    }
}
