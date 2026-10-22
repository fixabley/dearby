package com.dearby.nativeapp.pages.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.CardContent
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState

enum class MyCardsPhase { SIGNED_OUT, NO_CARD, LOADING, LOADED, FAILED }
/** One of the account's published cards as a grid tile: 명함 이름, 프로필 이름, 직무. */
data class MyCardChoice(val id: String, val title: String, val person: String, val job: String)
/** 내 명함: the signed-in account's published cards, newest first, the selected one rendered as a card below the grid. */
data class MyCardsState(
    val phase: MyCardsPhase = MyCardsPhase.LOADING, val cards: List<MyCardChoice> = emptyList(), val selectedId: String? = null, val selected: CardState? = null,
    /** Why the last 이 명함 지우기 failed, shown under the buttons. */
    val message: String? = null,
)

/**
 * 내 명함 tab (contract "명함 표현" 2026-10-18): a 2-column grid whose first tile is `+ 새 명함`; tapping a tile [select]s it and the card
 * shows under the grid with `QR 보이기` ([showQr], contract "명함 교환"), `이 명함 고치기` ([edit] opens the composer prefilled and saves onto the same card, contract "명함 고치기는 같은 명함을 수정" 2026-10-20) and `이 명함 지우기` ([delete] after a confirmation
 * dialog; the public link stops working). The grid is the page's only scroller.
 */
@Composable fun MyCardsPage(
    state: MyCardsState, create: () -> Unit, select: (String) -> Unit, showQr: (String) -> Unit, edit: (String) -> Unit, delete: (String) -> Unit,
    contact: (ContactState) -> Unit, retry: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    confirmDelete?.let { id ->
        AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text("이 명함을 지울까요?") }, text = { Text("공개 링크도 더 열리지 않아요. 되돌릴 수 없어요.") },
            confirmButton = { TextButton({ confirmDelete = null; delete(id) }) { Text("지우기", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("취소") } })
    }
    // No title of its own since 2026-10-20 (contract "명함 탭 통합"): the 명함 tab's top bar and segment row sit above this content.
    Column(Modifier.fillMaxSize()) {
        when (state.phase) {
            // The 2-column tiles, then the selected card (contract "내용 로딩은 스켈레톤").
            MyCardsPhase.LOADING -> DearbySkeleton(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { repeat(2) { SkeletonBlock(Modifier.weight(1f).height(96.dp), RoundedCornerShape(12.dp)) } }
                SkeletonCardBody(Modifier.padding(top = 10.dp))
            }
            MyCardsPhase.FAILED -> DearbyErrorState("명함을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.", retry, Modifier.fillMaxSize())
            // Empty / signed out: a CenteredMessage on the page background, no card (contract "비스크롤 화면 배치·CTA").
            MyCardsPhase.SIGNED_OUT, MyCardsPhase.NO_CARD -> DearbyEmptyState("내 명함을 만드세요", "프로필 데이터로 명함을 꾸며 발행해요. 발행할 때 로그인해요.", Modifier.fillMaxSize(), Icons.Rounded.Badge, MessageAction("명함 만들기", create))
            MyCardsPhase.LOADED -> LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { NewCardTile(create) }
                items(state.cards, key = { it.id }) { CardTile(it, it.id == state.selectedId) { select(it.id) } }
                state.selected?.let { card ->
                    item(span = { GridItemSpan(maxLineSpan) }) { CardContent(card, Modifier.padding(top = 10.dp), onContact = contact) }
                    item(span = { GridItemSpan(maxLineSpan) }) { DearbyButton({ showQr(card.id) }, Modifier.fillMaxWidth()) { Text("QR 보이기") } }
                    item(span = { GridItemSpan(maxLineSpan) }) { DearbyOutlineButton({ edit(card.id) }, Modifier.fillMaxWidth()) { Text("이 명함 고치기") } }
                    item(span = { GridItemSpan(maxLineSpan) }) { Text("고친 내용은 이 명함에 바로 반영돼요. QR과 링크는 그대로예요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TextButton({ confirmDelete = card.id }, Modifier.fillMaxWidth()) { Text("이 명함 지우기", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
                    }
                    state.message?.let { item(span = { GridItemSpan(maxLineSpan) }) { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) } }
                }
            }
        }
    }
}

@Composable private fun NewCardTile(create: () -> Unit) {
    Surface(onClick = create, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(4.dp))
            Text("새 명함", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** 선택 칸은 민트 바탕·청록 테두리, 나머지는 흰 바탕·구분선 테두리. */
@Composable private fun CardTile(card: MyCardChoice, selected: Boolean, select: () -> Unit) {
    Surface(onClick = select, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).semantics { this.selected = selected }, shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(card.person, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (card.job.isNotBlank()) Text(card.job, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}
