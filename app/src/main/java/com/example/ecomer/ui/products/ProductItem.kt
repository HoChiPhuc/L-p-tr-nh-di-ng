package com.example.ecomer.ui.products

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.example.ecomer.R
import com.example.ecomer.data.model.Product
import com.example.ecomer.ui.theme.DeleteRed
import com.example.ecomer.ui.theme.EditYellow
import com.example.ecomer.util.formatPrice

/** Một dòng sản phẩm: ảnh thu nhỏ, tên, giá, loại; Admin có thêm nút sửa/xóa. */
@Composable
fun ProductItem(
    product: Product,
    isAdmin: Boolean,
    isBeingEdited: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isBeingEdited) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val placeholder = painterResource(R.drawable.ic_image_placeholder)
            AsyncImage(
                model = product.image.ifBlank { null },
                contentDescription = "Ảnh sản phẩm ${product.name}",
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder,
                fallback = placeholder
            )
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                LabeledText("Tên sp: ", product.name)
                LabeledText("Giá sp: ", formatPrice(product.price))
                LabeledText("Loại sp: ", product.category)
            }
            if (isAdmin) {
                Spacer(Modifier.width(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionIcon(color = EditYellow, onClick = onEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Sửa sản phẩm ${product.name}",
                            tint = EditYellow
                        )
                    }
                    ActionIcon(color = DeleteRed, onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Xóa sản phẩm ${product.name}",
                            tint = DeleteRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LabeledText(label: String, value: String) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(label) }
            append(value)
        },
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun ActionIcon(
    color: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .border(1.dp, color, shape),
        shape = shape,
        colors = IconButtonDefaults.iconButtonColors(containerColor = color.copy(alpha = 0.08f))
    ) { icon() }
}
