package com.dearby.nativeapp.shared.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** A QR code of [content] (zxing core, encoding only). The caller describes it for accessibility via [modifier]. */
@Composable fun QrImage(content: String, modifier: Modifier = Modifier) {
    val bitmap = remember(content) { encodeQr(content) }
    Image(bitmap, null, modifier, contentScale = ContentScale.Fit)
}

private fun encodeQr(content: String, size: Int = 640): ImageBitmap {
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val width = matrix.width
    val height = matrix.height
    val pixels = IntArray(width * height) { i -> if (matrix.get(i % width, i / width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}
