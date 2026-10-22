package com.dearby.nativeapp.pages.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.DearbyButton
import com.dearby.nativeapp.shared.ui.DearbyEmptyState
import com.dearby.nativeapp.shared.ui.DearbySkeleton
import com.dearby.nativeapp.shared.ui.SkeletonCardBody
import com.dearby.nativeapp.shared.ui.DearbyTopBar
import com.dearby.nativeapp.widgets.card.cardContent.CardContent
import com.dearby.nativeapp.widgets.card.cardContent.ContactState

/**
 * 공유받은 명함 보기 (contract "유니버설 링크·App Links·활동 공유·오픈그래프"): a card App Link not in the wallet, with the 받은 명함 detail's card face,
 * `<프로그램> <활동>에서 받은 명함` above it when the link names an activity, and `받은 명함에 저장` ([save]) below — or the own-card notice instead.
 * A failed read shows the same wording as the save notices. The overflow menu holds 신고하기 ([report]) like the received detail.
 */
@Composable fun SharedCardPage(shared: SharedCard, close: () -> Unit, contact: (ContactState) -> Unit, report: (String, String?) -> Unit, save: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var reporting by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        DearbyTopBar("공유받은 명함", close) {
            if (shared.card != null && !shared.own) Box {
                IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "더 보기") }
                DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("신고하기") }, { menu = false; reporting = true }) }
            }
        }
        val card = shared.card
        when {
            card != null -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shared.origin?.let { Text("${it}에서 받은 명함", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                CardContent(card, onContact = contact)
                if (shared.own) Text("내 명함이에요.", Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                else DearbyButton(save, Modifier.fillMaxWidth()) { Text("받은 명함에 저장") }
            }
            shared.error != null -> DearbyEmptyState("명함을 볼 수 없어요", shared.error, Modifier.weight(1f), icon = Icons.Rounded.Badge)
            else -> DearbySkeleton(Modifier.weight(1f).padding(20.dp)) { SkeletonCardBody() }
        }
    }
    if (reporting) ReportDialog({ reporting = false }) { reason, detail -> reporting = false; report(reason, detail) }
}
