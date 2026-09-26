package com.dearby.nativeapp.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dearby.nativeapp.entities.card.model.ExchangeContextModel
import com.dearby.nativeapp.features.qr.QrActions
import com.dearby.nativeapp.pages.login.LoginPage
import com.dearby.nativeapp.pages.profile.ProfilePage
import com.dearby.nativeapp.pages.qr.*
import com.dearby.nativeapp.pages.wallet.*
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.*
import kotlinx.coroutines.*

@Composable fun DearbyApp(model: DearbyViewModel, incoming: String?, consume: () -> Unit) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var route by rememberSaveable { mutableStateOf("") }
    var returnRoute by rememberSaveable { mutableStateOf("") }
    var recipient by remember { mutableStateOf<CardState?>(null) }
    var detail by remember { mutableStateOf<CardState?>(null) }
    var enlarged by rememberSaveable { mutableStateOf(false) }
    var contextLabel by rememberSaveable { mutableStateOf("") }
    var input by rememberSaveable { mutableStateOf("") }
    val cards = state.cards.map { it.toState() }
    val chosen = cards.find { it.id == state.selectedCardId }
    val link = chosen?.let { QrActions.link(it.id, ExchangeContextModel(label = contextLabel.ifBlank { null })) }
    val bitmap = remember(link) { link?.let(QrActions::bitmap) }
    fun receive(text: String) {
        runCatching { QrActions.parse(text) }.onSuccess { (id, activity) -> model.receive(id, activity); route = ""; tab = 3 }.onFailure { model.report(it.message ?: "QR을 읽을 수 없습니다.") }
    }
    fun readImage(bitmap: android.graphics.Bitmap?) {
        if (bitmap == null) return
        scope.launch {
            try { val text = withContext(Dispatchers.Default) { QrActions.decode(bitmap) }; input = text; route = "receive" }
            catch (_: Exception) { model.report("사진에서 QR을 찾지 못했습니다. 선명한 QR 사진을 선택해 주세요.") }
        }
    }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { scope.launch {
            try {
                val decoded = withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(it)?.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
                    val options = BitmapFactory.Options().apply { inSampleSize = (maxOf(bounds.outWidth, bounds.outHeight) / 1600).coerceAtLeast(1) }
                    context.contentResolver.openInputStream(it)?.use { stream -> BitmapFactory.decodeStream(stream, null, options) }
                }
                readImage(decoded)
            } catch (_: Exception) { model.report("사진을 열 수 없습니다.") }
        } }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { readImage(it) }
    var exportLink by rememberSaveable { mutableStateOf<String?>(null) }
    val saveImage = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        val value = exportLink
        if (uri != null && value != null) scope.launch {
            try { withContext(Dispatchers.IO) { QrActions.save(context, uri, value) }; model.report("QR 이미지를 저장했습니다.") }
            catch (_: Exception) { model.report("QR 이미지를 저장하지 못했습니다.") }
        }
    }
    LaunchedEffect(incoming) { if (incoming != null) { input = incoming; route = "receive"; consume() } }
    LaunchedEffect(state.loggedIn) { if (state.loggedIn && route == "login") route = "" }
    LaunchedEffect(state.message) { state.message?.let { snackbars.showSnackbar(it); model.clearMessage() } }
    fun create() { if (!state.loggedIn) route = "login" else { returnRoute = route; route = "create" } }
    BackHandler(route.isNotEmpty()) { route = if (route == "create") returnRoute else "" }
    if (enlarged && bitmap != null) Dialog({ enlarged = false }, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.White).clickable { enlarged = false }, contentAlignment = Alignment.Center) { Image(bitmap.asImageBitmap(), "QR 확대. 누르면 돌아갑니다.", Modifier.fillMaxWidth().aspectRatio(1f)) }
    }
    detail?.let { card -> Dialog({ detail = null }) { CardContent(card, true, { detail = null }, Modifier.fillMaxWidth().heightIn(max = 650.dp)) } }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = { Column(Modifier.statusBarsPadding().fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text("dearby", Modifier.padding(10.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.headlineSmall); if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth()) } },
        bottomBar = { if (route.isEmpty() && !state.importVisible) NavigationBar {
            val names = listOf("발견", "저장", "QR", "받은 명함", "내 프로필")
            val icons = listOf(Icons.Outlined.Explore, Icons.Outlined.BookmarkBorder, Icons.Outlined.QrCode, Icons.Outlined.Badge, Icons.Outlined.PersonOutline)
            names.forEachIndexed { index, name -> NavigationBarItem(tab == index, { tab = index }, { Icon(icons[index], name) }, label = { Text(name, maxLines = 1, style = MaterialTheme.typography.labelSmall) }) }
        } }
    ) { padding -> Box(Modifier.padding(padding).fillMaxSize()) {
        when {
            !state.ready -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.importVisible && state.loggedIn -> ImportPage(state.guests.map { guest -> val cached = state.guestCards[guest.cardId]; ImportEntryState(guest.cardId, cached?.profileName ?: "명함 정보 확인 필요", cached?.job.orEmpty(), guest.context.label ?: guest.context.activityId.orEmpty()) }, state.busy, model::importSelected, { model.showImport(false) })
            route == "login" -> LoginPage(state.busy, state.challengeId, state.challengeExpires, model::requestCode, model::login, { route = "" })
            route == "create" -> CardEditor(state.profile, state.busy, { model.publish(it) { route = returnRoute } }, { route = returnRoute }, { Toast.makeText(context, "선택 해제한 연락처는 명함에 표시되지 않습니다.", Toast.LENGTH_SHORT).show() })
            route == "send" && recipient != null -> SendPage(cards, state.selectedCardId, recipient!!.person, state.busy, model::selectCard, { card, label -> model.send(card, recipient!!.ownerId, ExchangeContextModel(label = label.ifBlank { null })) { route = "" } }, ::create, { route = "" })
            route == "receive" -> FormColumn {
                TextButton({ route = "" }) { Text("닫기") }
                Text("QR 찍기", style = MaterialTheme.typography.headlineMedium)
                Row { Button({ runCatching { camera.launch(null) }.onFailure { model.report("사용 가능한 카메라 앱이 없습니다.") } }) { Text("카메라로 촬영") }; TextButton({ photo.launch("image/*") }) { Text("QR 사진 선택") } }
                Field("Dearby 명함 링크", input, { input = it }, singleLine = false)
                Text("명함을 확인한 뒤 이 기기에 ID를 저장합니다. 앱 삭제 시 기기 저장은 복구할 수 없습니다.")
                Button({ receive(input) }, enabled = !state.busy && input.isNotBlank()) { Text("명함 확인하고 기기에 저장") }
            }
            tab == 0 -> EmptyPanel("발견", "모집 중 활동을 준비하고 있습니다. 활동 수집 서버 연결 전이며 신청·캘린더 확인을 제공하지 않습니다.")
            tab == 1 -> EmptyPanel("저장", "저장한 활동이 없습니다. 활동 탐색과 저장 연동은 준비 중입니다.")
            tab == 2 -> QrPage(cards, state.selectedCardId, bitmap?.asImageBitmap(), model::selectCard, { enlarged = true }, { detail = it }, ::create,
                { link?.let { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, it) }, "명함 링크 공유")) } },
                { link?.let { (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Dearby 명함", it)); model.report("명함 링크를 복사했습니다.") } },
                { exportLink = link; saveImage.launch("dearby-qr.png") }, contextLabel, { contextLabel = it }, { route = "receive" })
            tab == 3 -> {
                val entries = if (state.loggedIn) state.wallet.map { WalletEntryState(it.id, it.card.toState(), it.context.label ?: it.context.activityId.orEmpty(), it.receivedAt, it.reciprocal) } else state.guests.mapNotNull { guest -> state.guestCards[guest.cardId]?.let { WalletEntryState(guest.cardId, it.toState(), guest.context.label ?: guest.context.activityId.orEmpty(), guest.savedAt, false) } }
                WalletPage(entries, state.loggedIn, state.guests.size, { route = "login" }, { model.showImport(true) }, model::refresh) { recipient = it; route = "send" }
            }
            else -> ProfilePage(state.profile, state.busy, state.loggedIn, model::saveProfile, { route = "login" }, model::logout, { model.showImport(true) })
        }
    } }
}
