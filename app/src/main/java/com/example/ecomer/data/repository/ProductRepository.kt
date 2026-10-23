package com.example.ecomer.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.example.ecomer.data.model.Product
import com.example.ecomer.data.remote.CloudinaryUploader
import com.example.ecomer.util.AppException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Dữ liệu sản phẩm đã được kiểm tra hợp lệ. */
data class ProductInput(val name: String, val category: String, val price: Double)

/**
 * Quản lý sản phẩm:
 * - Dữ liệu lưu trên Cloud Firestore (collection "products") với đúng 4 trường: name, category, price, image.
 * - Ảnh được tải lên Cloudinary (unsigned preset) và lưu URL an toàn (secure_url) vào Firestore.
 */
class ProductRepository(
    private val firestore: FirebaseFirestore,
    private val contentResolver: ContentResolver
) {
    private val collection get() = firestore.collection(Product.COLLECTION)

    /** Lắng nghe realtime danh sách sản phẩm từ Firestore. */
    fun observeProducts(): Flow<List<Product>> = callbackFlow {
        val registration = collection.addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
            } else if (snapshot != null) {
                trySend(
                    snapshot.documents
                        .map { it.toProduct() }
                        .sortedBy { it.name.lowercase() }
                )
            }
        }
        awaitClose { registration.remove() }
    }

    /**
     * Thêm sản phẩm mới lên Firestore.
     * Nếu có chọn ảnh -> tải lên Cloudinary lấy secure_url rồi ghi vào trường image.
     */
    suspend fun addProduct(input: ProductInput, imageUri: Uri?) {
        val uploadedUrl = if (imageUri != null) {
            CloudinaryUploader.uploadImage(imageUri, contentResolver)
        } else {
            ""
        }
        firestoreWrite {
            collection.add(input.toData(uploadedUrl)).await()
        }
    }

    /**
     * Cập nhật đúng document [product.id].
     * Nếu chọn ảnh mới -> tải lên Cloudinary và cập nhật trường image.
     * Nếu không chọn ảnh mới -> bảo lưu nguyên vẹn URL ảnh cũ [product.image].
     */
    suspend fun updateProduct(product: Product, input: ProductInput, newImageUri: Uri?) {
        val finalImageUrl = if (newImageUri != null) {
            CloudinaryUploader.uploadImage(newImageUri, contentResolver)
        } else {
            product.image
        }
        firestoreWrite {
            collection.document(product.id).update(input.toData(finalImageUrl)).await()
        }
    }

    /**
     * Xóa sản phẩm khỏi Firestore.
     * Không gọi Firebase Storage; chỉ xóa document Firestore an toàn.
     */
    suspend fun deleteProduct(product: Product): Boolean {
        firestoreWrite {
            collection.document(product.id).delete().await()
        }
        return true
    }

    // ---------------------------------------------------------------- helpers

    private suspend fun firestoreWrite(block: suspend () -> Unit) {
        try {
            withTimeout(REQUEST_TIMEOUT_MS) { block() }
        } catch (e: TimeoutCancellationException) {
            throw WriteTimeoutException()
        }
    }

    private fun ProductInput.toData(imageUrl: String): Map<String, Any> = mapOf(
        Product.FIELD_NAME to name,
        Product.FIELD_CATEGORY to category,
        Product.FIELD_PRICE to price,
        Product.FIELD_IMAGE to imageUrl
    )

    private fun DocumentSnapshot.toProduct() = Product(
        id = id,
        name = getString(Product.FIELD_NAME).orEmpty(),
        category = getString(Product.FIELD_CATEGORY).orEmpty(),
        price = getDouble(Product.FIELD_PRICE) ?: 0.0,
        image = getString(Product.FIELD_IMAGE).orEmpty()
    )

    private class WriteTimeoutException : AppException(
        "Chưa nhận được phản hồi từ máy chủ Firestore (quá thời gian chờ hoặc mất mạng). " +
            "Dữ liệu có thể sẽ được đồng bộ khi kết nối mạng ổn định trở lại."
    )

    private companion object {
        const val REQUEST_TIMEOUT_MS = 20_000L
    }
}
