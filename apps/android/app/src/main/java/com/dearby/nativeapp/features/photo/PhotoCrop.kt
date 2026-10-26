package com.dearby.nativeapp.features.photo

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as GeometryRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.dearby.nativeapp.shared.ui.DearbyButton
import com.dearby.nativeapp.shared.ui.DearbyFieldError
import com.dearby.nativeapp.shared.ui.DearbyTopBar
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The picked photo upright (EXIF orientation applied, contract "프로필 사진": the server does not read it) and no longer than [maxSide] on its long
 * side, so a 48MP photo does not take hundreds of MB; the upload is at most 1024px anyway. Null when the file is not an image this Android can decode
 * (HEIC needs Android 9). Blocking: call it off the main thread.
 */
fun decodeForCrop(resolver: ContentResolver, uri: Uri, maxSide: Int = 2048): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val orientation = resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
    val matrix = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }
    if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}.getOrNull()

/** The chosen square of [bitmap] as a JPEG no larger than [maxSide] (contract "프로필 사진": 한 변 1024px 이하), on white for transparent images. */
fun squareJpeg(bitmap: Bitmap, square: Rect, maxSide: Int = 1024): ByteArray {
    val side = min(square.width(), maxSide)
    val result = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
    Canvas(result).apply { drawColor(android.graphics.Color.WHITE); drawBitmap(bitmap, square, Rect(0, 0, side, side), Paint(Paint.FILTER_BITMAP_FLAG)) }
    return ByteArrayOutputStream().also { result.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
}

private const val MAX_ZOOM = 5f

/**
 * 사진 자르기 (contract "프로필 사진": 사용자가 위치·크기를 직접 조절, 화면에는 원형): the photo fills the circle; pinch (or the slider) zooms, a drag
 * moves it, and it can never leave part of the circle empty. [done] gets the square under the circle in [bitmap]'s pixels.
 */
@Composable fun PhotoCropPage(bitmap: Bitmap, busy: Boolean, error: String?, cancel: () -> Unit, done: (Rect) -> Unit) {
    BackHandler(onBack = cancel)
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var zoom by remember(bitmap) { mutableFloatStateOf(1f) }
    var offset by remember(bitmap) { mutableStateOf(Offset.Zero) }
    val margin = with(LocalDensity.current) { 24.dp.toPx() }
    var area by remember { mutableStateOf(Size.Zero) }
    // Display pixels per bitmap pixel at zoom 1: the shorter side just covers the circle.
    val circle = max(0f, min(area.width, area.height) - 2 * margin)
    val cover = if (circle > 0) circle / min(bitmap.width, bitmap.height) else 0f
    fun clamp(o: Offset, z: Float): Offset {
        val s = cover * z
        val x = max(0f, (bitmap.width * s - circle) / 2)
        val y = max(0f, (bitmap.height * s - circle) / 2)
        return Offset(o.x.coerceIn(-x, x), o.y.coerceIn(-y, y))
    }
    Column(Modifier.fillMaxSize()) {
        DearbyTopBar("사진 자르기", cancel)
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(Color.Black)
            .semantics { contentDescription = "자를 사진. 두 손가락으로 확대하고 끌어서 위치를 맞춰요." }
            .onSizeChanged { area = it.toSize() }
            // Keyed on what the gesture reads that is not state, so it never works with a stale circle.
            .pointerInput(bitmap, circle, busy) {
                detectTransformGestures { centroid, pan, gestureZoom, _ ->
                    if (busy) return@detectTransformGestures
                    val next = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                    // Zoom about the fingers: the point under the centroid stays under it.
                    val focus = centroid - Offset(size.width / 2f, size.height / 2f)
                    offset = clamp((offset - focus) * (next / zoom) + focus + pan, next)
                    zoom = next
                }
            }) {
            Canvas(Modifier.fillMaxSize()) {
                val s = cover * zoom
                val drawn = Size(bitmap.width * s, bitmap.height * s)
                val topLeft = center + offset - Offset(drawn.width / 2, drawn.height / 2)
                drawImage(image, dstOffset = IntOffset(topLeft.x.roundToInt(), topLeft.y.roundToInt()), dstSize = IntSize(drawn.width.roundToInt(), drawn.height.roundToInt()),
                    filterQuality = FilterQuality.Medium)
                // Dim everything outside the circle; the square uploaded is the circle's bounds.
                val hole = GeometryRect(center, circle / 2)
                drawPath(Path().apply { fillType = PathFillType.EvenOdd; addRect(GeometryRect(Offset.Zero, size)); addOval(hole) }, Color.Black.copy(alpha = .6f))
                drawCircle(Color.White.copy(alpha = .8f), circle / 2, center, style = Stroke(1.dp.toPx()))
            }
        }
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("두 손가락으로 확대하고 끌어서 위치를 맞춰요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            // The same zoom for one hand and for TalkBack, where pinching is not available.
            Slider(zoom, { zoom = it; offset = clamp(offset, it) }, Modifier.fillMaxWidth().semantics { contentDescription = "확대" }, enabled = !busy, valueRange = 1f..MAX_ZOOM)
            error?.let { DearbyFieldError(it) }
            DearbyButton({
                val s = cover * zoom
                if (s <= 0f) return@DearbyButton
                val side = (circle / s).roundToInt().coerceIn(1, min(bitmap.width, bitmap.height))
                val left = ((bitmap.width * s / 2 - offset.x - circle / 2) / s).roundToInt().coerceIn(0, bitmap.width - side)
                val top = ((bitmap.height * s / 2 - offset.y - circle / 2) / s).roundToInt().coerceIn(0, bitmap.height - side)
                done(Rect(left, top, left + side, top + side))
            }, Modifier.fillMaxWidth(), enabled = !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp) else Text("이 사진 쓰기")
            }
        }
    }
}
