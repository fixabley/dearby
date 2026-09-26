package com.dearby.nativeapp.pages.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material3.*
import com.dearby.nativeapp.features.contact.ContactActionState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.widgets.card.cardContent.CardContent
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.shared.ui.Field
import kotlinx.coroutines.launch

data class WalletEntryState(val id: String, val card: CardState, val context: String, val date: String, val reciprocal: Boolean)
fun walletMatches(entry: WalletEntryState, query: String): Boolean = listOf(entry.card.person, entry.card.job, entry.card.title, entry.context).any { it.contains(query, ignoreCase = true) }
@Composable fun WalletPage(entries: List<WalletEntryState>, loggedIn: Boolean, pendingCount: Int, login: () -> Unit, import: () -> Unit, refresh: () -> Unit, send: (CardState) -> Unit, onContact: (ContactActionState) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = entries.filter { walletMatches(it, query) }
    val showGroups = entries.any { !it.reciprocal }
    val groups = if (showGroups) listOf(filtered.filterNot { it.reciprocal }, filtered.filter { it.reciprocal }) else listOf(filtered)
    val horizontal = rememberPagerState { groups.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("받은 명함", style = MaterialTheme.typography.headlineMedium)
        Field("이름·직무·활동 검색", query, { query = it })
        Row { TextButton(refresh) { Text("새로고침") }; TextButton(if (loggedIn) import else login) { Text(if (loggedIn) "기기 명함 가져오기 ($pendingCount)" else "로그인") } }
        if (!loggedIn) Text("기기에 저장한 명함은 앱 삭제 시 복구할 수 없습니다.", style = MaterialTheme.typography.bodySmall)
        if (showGroups) Row { listOf("내 명함을 주지 않은 상대", "서로 주고받은 상대").forEachIndexed { index, title -> FilterChip(horizontal.currentPage == index, { scope.launch { horizontal.animateScrollToPage(index) } }, { Text(title, style = MaterialTheme.typography.labelSmall) }) } }
        HorizontalPager(horizontal, Modifier.weight(1f)) { group ->
            val cards = groups[group]
            if (cards.isEmpty()) Text("표시할 명함이 없습니다.") else {
                val vertical = rememberPagerState { cards.size }
                VerticalPager(vertical, Modifier.fillMaxSize(), pageSpacing = 12.dp) { page ->
                    val entry = cards[page]
                    var expanded by remember(entry.id) { mutableStateOf(false) }
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) { CardContent(entry.card, expanded, { expanded = !expanded }, if (expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), onContact) }
                        Text("${entry.context.ifBlank { "활동 선택 안 함" }} · ${entry.date}", style = MaterialTheme.typography.bodySmall)
                        if (!entry.reciprocal) Button({ if (loggedIn) send(entry.card) else login() }, Modifier.fillMaxWidth()) { Text("나도 명함 주기") }
                        Text("${page + 1} / ${cards.size} · 위아래로 넘기기", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
