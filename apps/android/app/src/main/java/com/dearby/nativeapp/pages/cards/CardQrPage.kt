package com.dearby.nativeapp.pages.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.CenteredMessage
import com.dearby.nativeapp.shared.ui.QrImage
import com.dearby.nativeapp.shared.ui.DearbyTopBar

/** One applied activity the card can be handed out at (contract "명함 교환"). */
data class QrActivity(val id: String, val title: String)

/**
 * 내 명함 QR 보이기: the card's public [url] as a QR (the symbol of a [CenteredMessage], contract "비스크롤 화면 배치·CTA"), the card name as the
 * title, the explanation, and the activity chip ([selectedId] among [activities], or `활동 없이`) that the owner can change. Brightness is raised
 * by the route, not here.
 */
@Composable fun CardQrPage(cardTitle: String, url: String, activities: List<QrActivity>, selectedId: String?, select: (String?) -> Unit, close: () -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val selected = activities.firstOrNull { it.id == selectedId }
    Column(Modifier.fillMaxSize()) {
        DearbyTopBar("QR 보이기", close)
        CenteredMessage(null, cardTitle, "상대가 카메라로 QR을 읽으면 이 명함을 받아요.", Modifier.weight(1f).fillMaxWidth(),
            symbol = { QrImage(url, Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = "내 명함 QR 코드, 카메라로 읽으면 명함을 받아요" }) },
            content = {
                AssistChip({ picking = true }, label = { Text(selected?.title ?: "활동 없이") }, leadingIcon = { Icon(Icons.Rounded.Event, null, Modifier.size(18.dp)) })
                Text("받은 명함에는 고른 활동이 함께 표시되고, 맞교환하지 않으면 30일 뒤 회수돼요.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            })
    }
    if (picking) AlertDialog(onDismissRequest = { picking = false }, title = { Text("어느 활동에서 건네나요?") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton({ picking = false; select(null) }, Modifier.fillMaxWidth()) { Text("활동 없이", Modifier.fillMaxWidth(), textAlign = TextAlign.Start) }
                activities.forEach { TextButton({ picking = false; select(it.id) }, Modifier.fillMaxWidth()) { Text(it.title, Modifier.fillMaxWidth(), textAlign = TextAlign.Start) } }
                if (activities.isEmpty()) Text("신청 표시한 활동이 없어요. 내 활동에서 신청한 활동이 여기에 나와요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton({ picking = false }) { Text("닫기") } })
}
