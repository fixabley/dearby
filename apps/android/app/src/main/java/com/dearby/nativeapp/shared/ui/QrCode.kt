package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlin.math.floor

/** 문자열을 QR로 그린다. 모듈마다 사각형을 그려 확대해도 흐려지지 않는다. 접근성 이름은 쓰는 쪽이 붙인다. */
@Composable fun DearbyQrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 0))
    }
    Canvas(modifier) {
        // 모듈을 정수 픽셀로 맞춰 모듈 사이에 틈선이 생기지 않게 하고, 남는 여백은 가운데로 나눈다.
        val cell = floor(size.minDimension / matrix.width)
        val origin = Offset((size.width - cell * matrix.width) / 2, (size.height - cell * matrix.height) / 2)
        drawRect(Color.White)
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, origin + Offset(x * cell, y * cell), Size(cell, cell))
        }
    }
}
