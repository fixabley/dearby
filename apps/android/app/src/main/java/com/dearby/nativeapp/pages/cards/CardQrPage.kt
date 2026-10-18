package com.dearby.nativeapp.pages.cards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.QrImage
import com.dearby.nativeapp.shared.ui.Quiet
import com.dearby.nativeapp.shared.ui.ScreenHeader

/** One applied activity the card can be handed out at (contract "명함 교환"). */
data class QrActivity(val id: String, val title: String)

/**
 * 내 명함 QR 보이기: the card's public [url] as a QR, with the activity chip ([selectedId] among [activities], or `활동 없이`)
 * that the owner can change. Brightness is raised by the route, not here.
 */
@Composable fun CardQrPage(cardTitle: String, url: String, activities: List<QrActivity>, selectedId: String?, select: (String?) -> Unit, close: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val selected = activities.firstOrNull { it.id == selectedId }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("QR 보이기", close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(cardTitle, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            QrImage(url, Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = "내 명함 QR 코드, 카메라로 읽으면 명함을 받아요" })
            AssistChip({ picking = true }, label = { Text(selected?.title ?: "활동 없이") }, leadingIcon = { Icon(Icons.Outlined.Event, null, Modifier.size(18.dp)) })
            Text("상대가 카메라로 QR을 읽으면 이 명함을 받아요. 받은 명함에는 고른 활동이 함께 표시되고, 맞교환하지 않으면 30일 뒤 회수돼요.",
                color = Quiet, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (picking) AlertDialog(onDismissRequest = { picking = false }, title = { Text("어느 활동에서 건네나요?") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton({ picking = false; select(null) }, Modifier.fillMaxWidth()) { Text("활동 없이", Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
                activities.forEach { TextButton({ picking = false; select(it.id) }, Modifier.fillMaxWidth()) { Text(it.title, Modifier.fillMaxWidth(), textAlign = TextAlign.Start) } }
                if (activities.isEmpty()) Text("신청 표시한 활동이 없어요. 내 활동에서 신청한 활동이 여기에 나와요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton({ picking = false }) { Text("닫기") } })
}
