package com.dearby.nativeapp.pages.qr

import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.activity.contextPicker.ActivityContextPicker
import com.dearby.nativeapp.widgets.activity.contextPicker.ActivityChoiceState
import com.dearby.nativeapp.shared.ui.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun QrPage(cards: List<CardState>, selectedId: String?, image: ImageBitmap?, select: (String?) -> Unit, enlarge: () -> Unit, detail: (CardState) -> Unit, create: () -> Unit, share: () -> Unit, copy: () -> Unit, save: () -> Unit, contextLabel: String, contextChange: (String) -> Unit, receive: () -> Unit, activities: List<ActivityChoiceState> = emptyList(), activityId: String? = null, activityChange: (String?) -> Unit = {}) {
    var menu by remember { mutableStateOf(false) }
    if (menu) ModalBottomSheet(onDismissRequest = { menu = false }, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("명함 공유", style = MaterialTheme.typography.titleLarge)
            listOf(Triple("링크 공유", Icons.Outlined.IosShare, share), Triple("링크 복사", Icons.Outlined.ContentCopy, copy), Triple("QR 이미지 저장", Icons.Outlined.Download, save)).forEach { (label, icon, action) ->
                HorizontalDivider()
                TextButton({ menu = false; action() }, Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Icon(icon, null, tint = Teal); Spacer(Modifier.width(16.dp)); Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
    val card = cards.find { it.id == selectedId }
    FormColumn {
        Text("명함 교환", style = MaterialTheme.typography.headlineMedium)
        QrModeSwitch(true) { if (!it) receive() }
        if (card == null) {
            OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().padding(24.dp).heightIn(min = 310.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.Badge, null, Modifier.size(72.dp), tint = Teal)
                    Spacer(Modifier.height(24.dp))
                    Text("이번에 공유할\n명함을 만드세요.", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Text("보여줄 연락처와 활동 이력을 골라 담을 수 있어요.", Modifier.padding(vertical = 18.dp), color = Quiet, textAlign = TextAlign.Center)
                    DearbyButton(create, Modifier.fillMaxWidth()) { Text("새 명함 만들기") }
                }
            }
        } else {
            OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Line)) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(card.title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold); Text(card.description, color = Quiet, fontSize = 8.sp) }
                        IconButton({ menu = true }) { Icon(Icons.Outlined.IosShare, "공유 메뉴") }
                    }
                    image?.let { Image(it, "명함 QR, 누르면 확대", Modifier.fillMaxWidth().aspectRatio(1f).clickable(onClick = enlarge)) }
                    Text("ⓘ QR을 누르면 QR만 크게 보여요", color = Quiet, style = MaterialTheme.typography.bodySmall)
                }
            }
            DearbyButton({ detail(card) }, Modifier.fillMaxWidth()) { Text("명함 보기") }
        }
        Text("내 명함", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OutlinedButton({ select(null) }, Modifier.width(120.dp).heightIn(min = 112.dp), shape = RoundedCornerShape(12.dp)) { Text("＋\n새 명함") } }
            items(cards, key = { it.id }) { item ->
                Surface(onClick = { select(item.id) }, modifier = Modifier.width(120.dp).heightIn(min = 112.dp), color = if (item.id == selectedId) Mint else Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(if (item.id == selectedId) 1.5.dp else 1.dp, if (item.id == selectedId) Teal else Line)) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) { Icon(Icons.Outlined.PersonOutline, null, tint = Teal); Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis); Text(item.description, color = Quiet, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        var showContext by rememberSaveable { mutableStateOf(false) }
        TextButton({ showContext = !showContext }, Modifier.fillMaxWidth()) { Text("교환한 활동 · " + (activities.find { it.id == activityId }?.title ?: contextLabel.ifBlank { "선택 안 함" })) }
        if (showContext) ActivityContextPicker(activities, activityId, contextLabel) { id, label -> activityChange(id); contextChange(label) }
    }
}
