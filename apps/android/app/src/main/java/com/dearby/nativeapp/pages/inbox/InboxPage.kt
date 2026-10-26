package com.dearby.nativeapp.pages.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.activity.programCard.ProgramCard

/**
 * 알림 목록 (contract "알림 목록(발견 우상단 종)", 2026-10-25): like the discovery program list — one program card per program (logo, name,
 * organization, its unread count in the count slot), expanded only by a tap; inside, rows shaped like the discovery activity rows (name, kind chip,
 * received time, a teal dot while unread). `이번 주` then `지난 알림`; `모두 읽음` at the top right. A row opens the activity ([open] reads it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun InboxPage(state: InboxViewState, back: () -> Unit, open: (InboxRow) -> Unit, readAll: () -> Unit, retry: () -> Unit, loadMore: () -> Unit) {
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    Column(Modifier.fillMaxSize()) {
        DearbyTopBar("알림", back) { if (state.unread > 0) TextButton(readAll) { Text("모두 읽음") } }
        when {
            state.phase == InboxPhase.LOADING -> DearbySkeleton(Modifier.fillMaxSize().padding(horizontal = 20.dp)) { repeat(4) { SkeletonBlock(Modifier.fillMaxWidth().height(72.dp), RoundedCornerShape(20.dp)) } }
            state.phase == InboxPhase.FAILED -> DearbyErrorState("알림을 불러오지 못했어요", "연결을 확인한 뒤 다시 시도해 주세요.", retry, Modifier.fillMaxSize())
            state.recent.isEmpty() && state.older.isEmpty() -> DearbyEmptyState("아직 알림이 없어요", "즐겨찾기한 프로그램·활동의 소식이 여기에 모여요", Modifier.fillMaxSize(), Icons.Rounded.Notifications)
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("이번 주" to state.recent, "지난 알림" to state.older).forEach { (heading, cards) ->
                    if (cards.isNotEmpty()) item(key = heading) { Text(heading, Modifier.padding(top = 6.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium) }
                    items(cards, key = { "inbox-" + it.programId }) { card ->
                        val shown = card.programId in expanded
                        ProgramCard(card.title, card.organization, card.unread, 0, null, null, null, emptyList(), emptyList(), shown,
                            { expanded = if (shown) expanded - card.programId else expanded + card.programId }, logoUrl = card.logoUrl, category = card.category, categoryCode = card.categoryCode) {
                            card.rows.forEach { row -> InboxRowItem(row, open) }
                        }
                    }
                }
                if (state.nextCursor != null) item(key = "more") {
                    LaunchedEffect(state.recent.size + state.older.size) { loadMore() }
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
                }
            }
        }
    }
}

@Composable private fun InboxRowItem(row: InboxRow, open: (InboxRow) -> Unit) {
    Card({ open(row) }, Modifier.fillMaxWidth().semantics { if (row.unread) stateDescription = "읽지 않음" }, shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(8.dp).background(if (row.unread) TealFill else androidx.compose.ui.graphics.Color.Transparent, CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(row.kind, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(row.received, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
