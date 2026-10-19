package com.dearby.nativeapp.pages.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Badge
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
import androidx.compose.ui.text.style.TextAlign
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
 * shows under the grid with `QR 보이기` ([showQr], contract "명함 교환"), `이 명함 고치기` ([edit] republishes it with its template) and `이 명함 지우기` ([delete] after a confirmation
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
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("내 명함")
        when (state.phase) {
            MyCardsPhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            MyCardsPhase.FAILED -> Notice("명함을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.") {
                DearbyOutlineButton(retry, Modifier.fillMaxWidth()) { Text("다시 시도") }
            }
            MyCardsPhase.SIGNED_OUT, MyCardsPhase.NO_CARD -> Column(Modifier.padding(20.dp)) {
                OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().heightIn(min = 320.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Outlined.Badge, null, Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(24.dp))
                        Text("내 명함을\n만드세요.", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Text("프로필 데이터로 명함을 꾸며 발행해요. 발행할 때 패스키로 로그인해요.", Modifier.padding(vertical = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        DearbyButton(create, Modifier.fillMaxWidth()) { Text("명함 만들기") }
                    }
                }
            }
            MyCardsPhase.LOADED -> LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { NewCardTile(create) }
                items(state.cards, key = { it.id }) { CardTile(it, it.id == state.selectedId) { select(it.id) } }
                state.selected?.let { card ->
                    item(span = { GridItemSpan(maxLineSpan) }) { CardContent(card, Modifier.padding(top = 10.dp), onContact = contact) }
                    item(span = { GridItemSpan(maxLineSpan) }) { DearbyButton({ showQr(card.id) }, Modifier.fillMaxWidth()) { Text("QR 보이기") } }
                    item(span = { GridItemSpan(maxLineSpan) }) { DearbyOutlineButton({ edit(card.id) }, Modifier.fillMaxWidth()) { Text("이 명함 고치기") } }
                    item(span = { GridItemSpan(maxLineSpan) }) { Text("명함을 고치면 같은 꾸밈으로 새 명함을 발행해요. 이전 명함은 그대로 남아요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
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
    Surface(onClick = create, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(4.dp))
            Text("새 명함", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** 선택 칸은 민트 바탕·청록 테두리, 나머지는 흰 바탕·구분선 테두리. */
@Composable private fun CardTile(card: MyCardChoice, selected: Boolean, select: () -> Unit) {
    Surface(onClick = select, modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).semantics { this.selected = selected }, shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(card.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(card.person, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (card.job.isNotBlank()) Text(card.job, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun Notice(title: String, detail: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        action()
    }
}
