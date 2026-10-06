package com.dearby.nativeapp.features.scan

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer

private val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)

// HybridBinarizer suits camera frames but misses flat images such as screenshots, which the global one reads.
private fun decode(source: LuminanceSource): String? = listOf(::HybridBinarizer, ::GlobalHistogramBinarizer).firstNotNullOfOrNull { binarizer ->
    runCatching { MultiFormatReader().apply { setHints(hints) }.decodeWithState(BinaryBitmap(binarizer(source))).text }.getOrNull()
}

/** Reads a QR code from a photo picked with the system photo picker (no photo permission). */
fun decodeQr(bitmap: Bitmap): String? {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    // Transparent areas (PNG screenshots) would read as black; lay the picture on white first.
    for (i in pixels.indices) {
        val p = pixels[i]
        val a = p ushr 24
        if (a == 255) continue
        fun blend(c: Int) = (c * a + 255 * (255 - a)) / 255
        pixels[i] = (0xFF shl 24) or (blend(p shr 16 and 0xFF) shl 16) or (blend(p shr 8 and 0xFF) shl 8) or blend(p and 0xFF)
    }
    return decode(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))
}

/** Reads a QR code from a camera frame's luminance (Y) plane. */
fun decodeQr(luminance: ByteArray, width: Int, height: Int): String? =
    decode(PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false))
