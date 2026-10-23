package com.example.ecomer

import android.app.Application
import com.example.ecomer.data.repository.AuthRepository
import com.example.ecomer.data.repository.ProductRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** Application: khởi tạo và giữ các repository dùng chung. */
class EcomerApplication : Application() {
    val authRepository: AuthRepository by lazy {
        AuthRepository(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance())
    }

    val productRepository: ProductRepository by lazy {
        ProductRepository(
            firestore = FirebaseFirestore.getInstance(),
            contentResolver = contentResolver
        )
    }
}
