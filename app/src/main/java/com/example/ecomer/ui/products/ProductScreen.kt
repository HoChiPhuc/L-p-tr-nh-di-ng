package com.example.ecomer.ui.products

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ecomer.data.model.AppUser
import com.example.ecomer.data.model.Product
import com.example.ecomer.ui.theme.DeleteRed
import com.example.ecomer.ui.theme.SuccessGreen

/** Màn hình chính: form (chỉ Admin) + danh sách sản phẩm realtime từ Firestore. */
@Composable
fun ProductScreen(
    user: AppUser,
    viewModel: ProductViewModel,
    onSignOut: () -> Unit
) {
    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val pendingDelete by viewModel.pendingDelete.collectAsStateWithLifecycle()
    val isDeleting by viewModel.isDeleting.collectAsStateWithLifecycle()
    // Quyền lấy từ phiên đăng nhập (vai trò từ Firestore), không phải do người dùng chọn.
    val isAdmin = user.isAdmin

    val snackbarHostState = remember { SnackbarHostState() }
    var lastMessageIsError by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            lastMessageIsError = message.isError
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message.text)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = if (lastMessageIsError) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.inverseSurface,
                    contentColor = if (lastMessageIsError) MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item(key = "header") { Header(user = user, onSignOut = onSignOut) }

            if (isAdmin) {
                item(key = "form") {
                    ProductForm(
                        state = form,
                        onNameChange = viewModel::onNameChange,
                        onCategoryChange = viewModel::onCategoryChange,
                        onPriceChange = viewModel::onPriceChange,
                        onImageSelected = viewModel::onImageSelected,
                        onClearImage = viewModel::clearSelectedImage,
                        onSubmit = viewModel::submit,
                        onCancelEdit = viewModel::cancelEdit
                    )
                }
            }

            item(key = "list_title") {
                Text(
                    text = "Danh sách sản phẩm:",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            when (val state = listState) {
                ProductListState.Loading -> item(key = "loading") {
                    StatusBox { CircularProgressIndicator() }
                }

                is ProductListState.Error -> item(key = "error") {
                    StatusBox {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Không tải được danh sách sản phẩm",
                                fontWeight = FontWeight.Bold,
                                color = DeleteRed
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(state.message, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = viewModel::retryLoading) { Text("THỬ LẠI") }
                        }
                    }
                }

                is ProductListState.Success -> {
                    if (state.products.isEmpty()) {
                        item(key = "empty") {
                            StatusBox {
                                Text(
                                    text = if (isAdmin) "Chưa có sản phẩm nào. Hãy thêm sản phẩm đầu tiên!"
                                    else "Chưa có sản phẩm nào.",
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(state.products, key = { it.id }) { product ->
                            ProductItem(
                                product = product,
                                isAdmin = isAdmin,
                                isBeingEdited = form.editingProduct?.id == product.id,
                                onEdit = { viewModel.startEdit(product) },
                                onDelete = { viewModel.requestDelete(product) }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { product ->
        DeleteDialog(
            product = product,
            isDeleting = isDeleting,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::dismissDelete
        )
    }
}

@Composable
private fun Header(user: AppUser, onSignOut: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (user.isAdmin) SuccessGreen else MaterialTheme.colorScheme.secondary
                ) {
                    Text(
                        text = if (user.isAdmin) "ADMIN" else "NGƯỜI DÙNG (chỉ xem)",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            IconButton(onClick = onSignOut) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Đăng xuất",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Dữ liệu sản phẩm",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun StatusBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

@Composable
private fun DeleteDialog(
    product: Product,
    isDeleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xóa sản phẩm") },
        text = {
            Text("Bạn có chắc chắn muốn xóa \"${product.name}\"? Hành động này không thể hoàn tác.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !isDeleting) {
                if (isDeleting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text("XÓA", color = DeleteRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDeleting) { Text("HỦY") }
        }
    )
}
