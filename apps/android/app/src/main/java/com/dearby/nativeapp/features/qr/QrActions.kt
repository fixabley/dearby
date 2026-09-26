package com.dearby.nativeapp.features.qr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.dearby.nativeapp.entities.card.model.ExchangeContextModel
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import java.util.UUID

object QrActions {
    fun link(id: String, context: ExchangeContextModel): String = Uri.Builder().scheme("dearby").authority("card").appendPath(id).apply {
        context.activityId?.let { appendQueryParameter("activityId", it) }
        context.label?.let { appendQueryParameter("label", it) }
    }.build().toString()
    fun parse(text: String): Pair<String, ExchangeContextModel> {
        val uri = Uri.parse(text.trim())
        require(uri.isHierarchical && uri.scheme == "dearby" && uri.encodedAuthority == "card" && uri.fragment == null && uri.pathSegments.size == 1) { "Dearby 명함 QR 또는 링크를 선택해 주세요." }
        val id = uri.pathSegments.single()
        require(uri.encodedPath == "/$id" && UUID.fromString(id).toString() == id.lowercase()) { "올바른 명함 ID가 아닙니다." }
        val names = uri.queryParameterNames
        require(names.all { it in setOf("activityId", "label") } && names.all { uri.getQueryParameters(it).size == 1 }) { "모호한 QR 교환 정보입니다." }
        val activityId = uri.getQueryParameter("activityId")
        activityId?.let { require(UUID.fromString(it).toString() == it.lowercase()) }
        val label = uri.getQueryParameter("label")
        require(label == null || label.length <= 200) { "활동 이름이 너무 깁니다." }
        return id to ExchangeContextModel(activityId, label)
    }
    fun bitmap(link: String): Bitmap {
        val bits = QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 800, 800, mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8"))
        return Bitmap.createBitmap(800, 800, Bitmap.Config.ARGB_8888).apply { setPixels(IntArray(800 * 800) { index -> if (bits[index % 800, index / 800]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }, 0, 800, 0, 0, 800, 800) }
    }
    fun decode(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(bitmap.width, bitmap.height, pixels))), mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE), DecodeHintType.TRY_HARDER to true)).text
    }
    fun save(context: Context, uri: Uri, link: String) {
        context.contentResolver.openOutputStream(uri)?.use { check(bitmap(link).compress(Bitmap.CompressFormat.PNG, 100, it)) } ?: error("이미지를 저장할 수 없습니다.")
    }
}
