package com.example.ecomer.ui.products

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ecomer.data.model.Product
import com.example.ecomer.data.model.SessionState
import com.example.ecomer.data.repository.AuthRepository
import com.example.ecomer.data.repository.ProductInput
import com.example.ecomer.data.repository.ProductRepository
import com.example.ecomer.util.parsePrice
import com.example.ecomer.util.priceToInput
import com.example.ecomer.util.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Trạng thái danh sách sản phẩm. */
sealed interface ProductListState {
    data object Loading : ProductListState
    data class Success(val products: List<Product>) : ProductListState
    data class Error(val message: String) : ProductListState
}

/** Trạng thái form thêm/sửa. [editingProduct] != null nghĩa là đang sửa. */
data class ProductFormState(
    val name: String = "",
    val category: String = "",
    val priceText: String = "",
    val selectedImageUri: Uri? = null,
    val editingProduct: Product? = null,
    val nameError: String? = null,
    val categoryError: String? = null,
    val priceError: String? = null,
    val isSubmitting: Boolean = false
) {
    val isEditing: Boolean get() = editingProduct != null
}

/** Thông báo ngắn (Snackbar) cho các thao tác. */
data class UiMessage(val text: String, val isError: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class ProductViewModel(
    private val productRepository: ProductRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val retryTrigger = MutableStateFlow(0)

    /** Danh sách realtime; chỉ lắng nghe Firestore khi đã đăng nhập và có UI đang quan sát. */
    val listState: StateFlow<ProductListState> = combine(authRepository.session, retryTrigger) { s, _ -> s }
        .flatMapLatest { session ->
            if (session is SessionState.SignedIn) productsFlow() else flowOf(ProductListState.Loading)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProductListState.Loading)

    private val _form = MutableStateFlow(ProductFormState())
    val form: StateFlow<ProductFormState> = _form.asStateFlow()

    private val _pendingDelete = MutableStateFlow<Product?>(null)
    val pendingDelete: StateFlow<Product?> = _pendingDelete.asStateFlow()

    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val messageChannel = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = messageChannel.receiveAsFlow()

    /** Quyền Admin lấy từ phiên đăng nhập (vai trò đọc từ Firestore users/{uid}). */
    val isAdmin: StateFlow<Boolean> = authRepository.session
        .map { (it as? SessionState.SignedIn)?.user?.isAdmin == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // Đăng xuất => xóa sạch form/hộp thoại để tài khoản sau không thấy dữ liệu cũ.
        viewModelScope.launch {
            authRepository.session.collect { session ->
                if (session !is SessionState.SignedIn) {
                    _form.value = ProductFormState()
                    _pendingDelete.value = null
                    _isDeleting.value = false
                }
            }
        }
    }

    private fun productsFlow(): Flow<ProductListState> =
        productRepository.observeProducts()
            .map<List<Product>, ProductListState> { ProductListState.Success(it) }
            .onStart { emit(ProductListState.Loading) }
            .catch { emit(ProductListState.Error(it.toUserMessage())) }

    fun retryLoading() {
        retryTrigger.update { it + 1 }
    }

    // ------------------------------------------------------------------ form

    fun onNameChange(value: String) = _form.update { it.copy(name = value, nameError = null) }
    fun onCategoryChange(value: String) = _form.update { it.copy(category = value, categoryError = null) }
    fun onPriceChange(value: String) = _form.update { it.copy(priceText = value, priceError = null) }
    fun onImageSelected(uri: Uri?) {
        if (uri != null) _form.update { it.copy(selectedImageUri = uri) }
    }

    fun clearSelectedImage() = _form.update { it.copy(selectedImageUri = null) }

    fun startEdit(product: Product) {
        if (!requireAdmin()) return
        _form.value = ProductFormState(
            name = product.name,
            category = product.category,
            priceText = priceToInput(product.price),
            editingProduct = product
        )
    }

    fun cancelEdit() {
        if (_form.value.isSubmitting) return
        _form.value = ProductFormState()
    }

    fun submit() {
        if (!requireAdmin()) return
        val current = _form.value
        if (current.isSubmitting) return

        val name = current.name.trim()
        val category = current.category.trim()
        val price = parsePrice(current.priceText)
        val nameError = if (name.isEmpty()) "Tên sản phẩm không được để trống" else null
        val categoryError = if (category.isEmpty()) "Loại sản phẩm không được để trống" else null
        val priceError = when {
            current.priceText.isBlank() -> "Vui lòng nhập giá sản phẩm"
            price == null -> "Giá phải là số hợp lệ"
            price <= 0.0 -> "Giá phải lớn hơn 0"
            else -> null
        }
        if (nameError != null || categoryError != null || priceError != null || price == null) {
            _form.update { it.copy(nameError = nameError, categoryError = categoryError, priceError = priceError) }
            return
        }

        val input = ProductInput(name, category, price)
        val editing = current.editingProduct
        _form.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            try {
                if (editing == null) {
                    productRepository.addProduct(input, current.selectedImageUri)
                } else {
                    productRepository.updateProduct(editing, input, current.selectedImageUri)
                }
                _form.value = ProductFormState()
                send(
                    if (editing == null) "Đã thêm sản phẩm thành công" else "Đã cập nhật sản phẩm thành công",
                    isError = false
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _form.update { it.copy(isSubmitting = false) }
                send(
                    (if (editing == null) "Thêm sản phẩm thất bại: " else "Cập nhật thất bại: ") + e.toUserMessage(),
                    isError = true
                )
            }
        }
    }

    // ---------------------------------------------------------------- delete

    fun requestDelete(product: Product) {
        if (requireAdmin()) _pendingDelete.value = product
    }

    fun dismissDelete() {
        if (!_isDeleting.value) _pendingDelete.value = null
    }

    fun confirmDelete() {
        if (!requireAdmin()) return
        val product = _pendingDelete.value ?: return
        if (_isDeleting.value) return
        _isDeleting.value = true
        viewModelScope.launch {
            try {
                productRepository.deleteProduct(product)
                // Nếu đang sửa đúng sản phẩm vừa xóa thì thoát chế độ sửa.
                if (_form.value.editingProduct?.id == product.id) _form.value = ProductFormState()
                send("Đã xóa sản phẩm thành công", isError = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                send("Xóa sản phẩm thất bại: ${e.toUserMessage()}", isError = true)
            } finally {
                _isDeleting.value = false
                _pendingDelete.value = null
            }
        }
    }

    private fun requireAdmin(): Boolean {
        if (isAdmin.value) return true
        send("Bạn không có quyền thực hiện thao tác này", isError = true)
        return false
    }

    private fun send(text: String, isError: Boolean) {
        messageChannel.trySend(UiMessage(text, isError))
    }
}
