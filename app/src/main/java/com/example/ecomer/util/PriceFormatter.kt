package com.example.ecomer.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val viSymbols = DecimalFormatSymbols(Locale.US).apply {
    groupingSeparator = '.'
    decimalSeparator = ','
}

/** Hiển thị giá, ví dụ 300000 -> "300.000 đ", 1234.5 -> "1.234,5 đ". */
fun formatPrice(price: Double): String {
    val pattern = if (price % 1.0 == 0.0) "#,##0" else "#,##0.##"
    return DecimalFormat(pattern, viSymbols).format(price) + " đ"
}

/** Chuyển ô nhập giá sang số. Chấp nhận "300000", "300.000" (dấu chấm ngăn nghìn) hoặc "1500,5". */
fun parsePrice(input: String): Double? {
    val text = input.trim().replace(" ", "")
    if (text.isEmpty()) return null
    val normalized = when {
        // Dạng ngăn nghìn kiểu VN: 1.234.567 hoặc 1.234.567,5
        Regex("""^\d{1,3}(\.\d{3})+(,\d+)?$""").matches(text) ->
            text.replace(".", "").replace(',', '.')
        text.contains(',') && !text.contains('.') -> text.replace(',', '.')
        else -> text
    }
    val value = normalized.toDoubleOrNull() ?: return null
    return value.takeIf { it.isFinite() }
}

/** Dạng chuỗi để đưa giá hiện có vào ô nhập khi sửa (bỏ ".0" thừa). */
fun priceToInput(price: Double): String =
    if (price % 1.0 == 0.0) price.toLong().toString() else price.toString()
