package com.example.ecomer.data.model

/**
 * Sản phẩm. Document Firestore (products/{id}) CHỈ chứa 4 trường:
 * name, category, price, image. [id] là ID document, không được ghi vào dữ liệu.
 */
data class Product(
    val id: String = "",
    val name: String = "",
    val category: String = "",
    val price: Double = 0.0,
    val image: String = ""
) {
    companion object {
        const val COLLECTION = "products"
        const val FIELD_NAME = "name"
        const val FIELD_CATEGORY = "category"
        const val FIELD_PRICE = "price"
        const val FIELD_IMAGE = "image"
    }
}
