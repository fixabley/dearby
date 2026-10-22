package com.dearby.nativeapp.pages.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.DearbyFieldError
import com.dearby.nativeapp.shared.ui.DearbyOutlineButton
import com.dearby.nativeapp.shared.ui.ExampleBadge
import com.dearby.nativeapp.shared.ui.DearbyTopBar
import com.dearby.nativeapp.widgets.card.cardContent.CardContent
import com.dearby.nativeapp.widgets.card.cardContent.ContactState

/**
 * One received card opened from the wallet list (contract "받은 명함 상세"·"명함 교환"): where it was received above the card face,
 * and below it `내 명함도 건네기` ([giveBack] with one of [myCards] as id·title, a picker when there are several) or the
 * `맞교환했어요` badge once the exchange is mutual. The top bar's overflow menu holds 신고하기 ([report] with a reason and optional detail) and
 * 삭제하고 차단하기 ([block] after a confirmation), contract "명함 신고·차단".
 */
@Composable fun WalletCardPage(received: ReceivedCard, myCards: List<Pair<String, String>>, busy: Boolean, message: String?,
                               close: () -> Unit, contact: (ContactState) -> Unit, report: (String, String?) -> Unit, block: () -> Unit, giveBack: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    var blocking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        DearbyTopBar("받은 명함", close) {
            Box {
                IconButton({ menu = true }, enabled = !busy) { Icon(Icons.Rounded.MoreVert, "더 보기") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("신고하기") }, { menu = false; reporting = true })
                    DropdownMenuItem({ Text("삭제하고 차단하기", color = MaterialTheme.colorScheme.error) }, { menu = false; blocking = true })
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(received.origin, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            CardContent(received.card, onContact = contact)
            if (received.reciprocal) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { ExampleBadge("맞교환했어요", accent = true) }
            else {
                DearbyOutlineButton({ if (myCards.size == 1) giveBack(myCards[0].first) else picking = true }, Modifier.fillMaxWidth(), enabled = !busy && myCards.isNotEmpty()) {
                    Text(if (busy) "건네는 중…" else "내 명함도 건네기")
                }
                Text(
                    if (myCards.isEmpty()) "먼저 명함 탭의 내 명함에서 명함을 만들면 건넬 수 있어요."
                    else "맞교환한 명함은 회수되지 않아요." + (received.badge?.let { " 지금은 맞교환하지 않으면 $it." } ?: ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                )
            }
            message?.let { DearbyFieldError(it) }
        }
    }
    if (reporting) ReportDialog({ reporting = false }) { reason, detail -> reporting = false; report(reason, detail) }
    if (blocking) AlertDialog(onDismissRequest = { blocking = false }, title = { Text("삭제하고 차단할까요?") },
        text = { Text("${received.card.person}님의 명함을 지우고, 앞으로 이 사람의 명함을 받지 않아요.") },
        confirmButton = { TextButton({ blocking = false; block() }) { Text("삭제하고 차단하기", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton({ blocking = false }) { Text("취소") } })
    if (picking) AlertDialog(onDismissRequest = { picking = false }, title = { Text("어느 명함을 건넬까요?") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                myCards.forEach { (id, title) -> TextButton({ picking = false; giveBack(id) }, Modifier.fillMaxWidth()) { Text(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Start) } }
            }
        },
        confirmButton = { TextButton({ picking = false }) { Text("취소") } })
}

private val reportReasons = listOf("SPAM" to "스팸·광고", "IMPERSONATION" to "다른 사람 사칭", "INAPPROPRIATE" to "부적절한 내용", "OTHER" to "기타")

/** 신고 사유 radio list; 기타 needs a description (up to 500 characters), the others may add one. */
@Composable internal fun ReportDialog(dismiss: () -> Unit, send: (String, String?) -> Unit) {
    var reason by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf("") }
    val ready = reason != null && (reason != "OTHER" || detail.isNotBlank())
    AlertDialog(onDismissRequest = dismiss, title = { Text("신고하기") },
        text = {
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                reportReasons.forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(reason == key, role = Role.RadioButton) { reason = key }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(reason == key, null)
                        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                OutlinedTextField(detail, { detail = it.take(500) }, Modifier.fillMaxWidth().padding(top = 8.dp),
                    label = { Text(if (reason == "OTHER") "설명 (필수)" else "설명 (선택)") }, supportingText = { Text("${detail.length}/500") }, minLines = 2)
            }
        },
        confirmButton = { TextButton({ reason?.let { send(it, detail.trim().ifBlank { null }) } }, enabled = ready) { Text("보내기") } },
        dismissButton = { TextButton(dismiss) { Text("취소") } })
}
