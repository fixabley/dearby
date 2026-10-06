package com.dearby.nativeapp.features.scan

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

private val hints = mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)

private fun decode(source: LuminanceSource): String? = runCatching {
    MultiFormatReader().apply { setHints(hints) }.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
}.getOrNull()

/** Reads a QR code from a photo picked with the system photo picker (no photo permission). */
fun decodeQr(bitmap: Bitmap): String? {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return decode(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))
}

/** Reads a QR code from a camera frame's luminance (Y) plane. */
fun decodeQr(luminance: ByteArray, width: Int, height: Int): String? =
    decode(PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false))
