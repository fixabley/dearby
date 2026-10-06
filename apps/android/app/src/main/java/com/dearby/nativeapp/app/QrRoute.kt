package com.dearby.nativeapp.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.entities.account.model.ScannedLink
import com.dearby.nativeapp.features.scan.QrCameraPreview
import com.dearby.nativeapp.features.scan.decodeQr
import com.dearby.nativeapp.pages.qr.QrPage
import com.dearby.nativeapp.shared.ui.DearbyChoice

/**
 * QR tab: shares the account's card on entry and scans received codes. The share button hands the link
 * to the system chooser; camera access is asked for only when scanning starts.
 */
@Composable fun QrRoute(model: QrShareViewModel, catalog: CatalogViewModel, create: () -> Unit, open: (ScannedLink) -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val applied by catalog.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var scanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableIntStateOf(0) }
    var camera by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val hasCamera = remember { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { camera = it }
    val scanned: (String) -> Unit = { text ->
        ScannedLink.parse(text, BuildConfig.WEB_ORIGIN)?.let { scanError = null; round++; open(it) } ?: run { scanError = "Dearby 명함 QR이 아니에요." }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
        }.getOrNull()
        bitmap?.let(::decodeQr)?.let(scanned) ?: run { scanError = "사진에서 QR을 찾지 못했어요." }
    }
    LaunchedEffect(Unit) { model.load() }
    LaunchedEffect(scanning) {
        if (!scanning) return@LaunchedEffect
        // Instrumented tests stand in for the camera with the text a QR would carry.
        ApiOrigin.debugScanText?.takeIf { BuildConfig.DEBUG }?.let { scanned(it); return@LaunchedEffect }
        if (hasCamera && !camera) ask.launch(Manifest.permission.CAMERA)
    }
    QrPage(state, applied.appliedActivities.map { DearbyChoice(it.id, it.title) }, model::select, model::toggle,
        onShare = { url ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url)
            context.startActivity(Intent.createChooser(send, null))
        }, retry = model::load, create = create,
        scanning = scanning, onScanning = { scanning = it; scanError = null },
        scanner = {
            when {
                hasCamera && camera -> key(round) { QrCameraPreview(scanned, Modifier.fillMaxSize()) }
                else -> Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (hasCamera) "카메라를 쓸 수 없어요" else "카메라가 없어요", color = Color.White, style = MaterialTheme.typography.titleMedium)
                    Text(if (hasCamera) "설정에서 Dearby의 카메라 접근을 켜거나 사진에서 스캔해 주세요." else "사진에서 스캔해 주세요.",
                        color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        pickPhoto = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, scanError = scanError)
}
