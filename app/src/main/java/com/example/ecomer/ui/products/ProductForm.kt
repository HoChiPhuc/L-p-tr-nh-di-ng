package com.example.ecomer.ui.products

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.ecomer.R

/** Khu vực nhập: tên, loại, giá, chọn ảnh + xem trước + nút Thêm/Cập nhật (chỉ Admin thấy). */
@Composable
fun ProductForm(
    state: ProductFormState,
    onNameChange: (String) -> Unit,
    onCategoryChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onImageSelected: (Uri?) -> Unit,
    onClearImage: () -> Unit,
    onSubmit: () -> Unit,
    onCancelEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val enabled = !state.isSubmitting
    val shape = RoundedCornerShape(8.dp)

    // Android Photo Picker (tự fallback về trình chọn tài liệu trên máy cũ), không cần xin quyền.
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        onImageSelected(uri)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.isEditing) {
            Text(
                text = "Đang sửa sản phẩm: ${state.editingProduct?.name.orEmpty()}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Tên sản phẩm") },
            singleLine = true,
            enabled = enabled,
            isError = state.nameError != null,
            supportingText = state.nameError?.let { { Text(it) } },
            shape = shape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
        )
        OutlinedTextField(
            value = state.category,
            onValueChange = onCategoryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Loại sản phẩm") },
            singleLine = true,
            enabled = enabled,
            isError = state.categoryError != null,
            supportingText = state.categoryError?.let { { Text(it) } },
            shape = shape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
        )
        OutlinedTextField(
            value = state.priceText,
            onValueChange = onPriceChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Giá sản phẩm") },
            singleLine = true,
            enabled = enabled,
            isError = state.priceError != null,
            supportingText = state.priceError?.let { { Text(it) } },
            shape = shape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
        )

        // Khu vực chọn ảnh
        val fileName = remember(state.selectedImageUri) { state.selectedImageUri?.let { displayName(context, it) } }
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(enabled = enabled) {
                    focusManager.clearFocus()
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_folder),
                    contentDescription = "Chọn ảnh",
                    modifier = Modifier.size(26.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = fileName ?: if (state.isEditing) "Chọn ảnh mới (bỏ trống để giữ ảnh cũ)" else "Chọn ảnh sản phẩm",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (fileName != null) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Xem trước ảnh: ảnh mới chọn, hoặc ảnh hiện tại khi đang sửa
        val previewModel: Any? = state.selectedImageUri
            ?: state.editingProduct?.image?.takeIf { it.isNotBlank() }
        if (previewModel != null) {
            val placeholder = painterResource(R.drawable.ic_image_placeholder)
            Text(
                text = if (state.selectedImageUri != null) "Ảnh xem trước:" else "Ảnh hiện tại:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AsyncImage(
                model = previewModel,
                contentDescription = "Ảnh xem trước sản phẩm",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(shape),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder
            )
            if (state.selectedImageUri != null) {
                TextButton(onClick = onClearImage, enabled = enabled) { Text("Bỏ ảnh đã chọn") }
            }
        }

        Button(
            onClick = {
                focusManager.clearFocus()
                onSubmit()
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = shape
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(10.dp))
                Text(if (state.selectedImageUri != null) "ĐANG TẢI ẢNH VÀ LƯU..." else "ĐANG LƯU...")
            } else {
                Text(
                    text = if (state.isEditing) "CẬP NHẬT SẢN PHẨM" else "THÊM SẢN PHẨM",
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (state.isEditing) {
            OutlinedButton(
                onClick = onCancelEdit,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = shape
            ) { Text("HỦY SỬA") }
        }
    }
}

/** Lấy tên tệp hiển thị từ Uri của Photo Picker. */
private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull() ?: uri.lastPathSegment
