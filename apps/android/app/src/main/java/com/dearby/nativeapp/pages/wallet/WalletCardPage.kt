package com.dearby.nativeapp.pages.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
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
 * `맞교환했어요` badge once the exchange is mutual.
 */
@Composable fun WalletCardPage(received: ReceivedCard, myCards: List<Pair<String, String>>, busy: Boolean, message: String?,
                               close: () -> Unit, contact: (ContactState) -> Unit, giveBack: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        DearbyTopBar("받은 명함", close)
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
    if (picking) AlertDialog(onDismissRequest = { picking = false }, title = { Text("어느 명함을 건넬까요?") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                myCards.forEach { (id, title) -> TextButton({ picking = false; giveBack(id) }, Modifier.fillMaxWidth()) { Text(title, Modifier.fillMaxWidth(), textAlign = TextAlign.Start) } }
            }
        },
        confirmButton = { TextButton({ picking = false }) { Text("취소") } })
}
