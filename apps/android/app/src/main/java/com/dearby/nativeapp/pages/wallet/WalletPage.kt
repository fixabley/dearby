package com.dearby.nativeapp.pages.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material3.*
import com.dearby.nativeapp.features.contact.ContactActionState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.widgets.card.cardContent.CardContent
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.shared.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import com.dearby.nativeapp.widgets.card.cardContent.CardStack
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
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Spacer(Modifier.width(48.dp)); Text("받은 명함", Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.titleLarge); IconButton(refresh) { Icon(Icons.Outlined.Refresh, "새로고침", tint = Quiet) } }
        TextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("이름·직무·활동 검색", style = MaterialTheme.typography.bodyMedium) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = MaterialTheme.shapes.medium, colors = TextFieldDefaults.colors(focusedContainerColor = Soft, unfocusedContainerColor = Soft, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
        if (pendingCount > 0 && loggedIn) TextButton(import) { Text("기기 명함 가져오기 ($pendingCount)") }
        if (!loggedIn) Text("기기에 저장한 명함은 앱 삭제 시 복구할 수 없습니다.", style = MaterialTheme.typography.bodySmall)
        if (showGroups) Row { listOf("내 명함을 주지 않은 상대", "서로 주고받은 상대").forEachIndexed { index, title -> Column(Modifier.weight(1f)) { TextButton({ scope.launch { horizontal.animateScrollToPage(index) } }, Modifier.fillMaxWidth()) { Text(title + " ${groups[index].map { it.card.ownerId }.distinct().size}", style = MaterialTheme.typography.labelSmall, color = if (horizontal.currentPage == index) Teal else Quiet) }; HorizontalDivider(thickness = if (horizontal.currentPage == index) 2.dp else 1.dp, color = if (horizontal.currentPage == index) Teal else Line) } } }
        HorizontalPager(horizontal, Modifier.weight(1f)) { group ->
            val cards = groups[group]
            if (cards.isEmpty()) Text("표시할 명함이 없습니다.") else {
                val vertical = rememberPagerState { cards.size }
                VerticalPager(vertical, Modifier.fillMaxSize().testTag("walletPager"), pageSpacing = 12.dp) { page ->
                    val entry = cards[page]
                    var expanded by remember(entry.id) { mutableStateOf(false) }
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CardStack(cards.map { it.card }, page, Modifier.weight(1f), onContact)
                        if (expanded) Dialog({ expanded = false }) { CardContent(entry.card, true, { expanded = false }, Modifier.fillMaxWidth().heightIn(max = 650.dp), onContact) }
                        Text("${entry.context.ifBlank { "활동 선택 안 함" }} · ${entry.date}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DearbyOutlineButton({ expanded = true }, Modifier.weight(1f)) { Text("명함 상세보기") }
                            if (!entry.reciprocal) DearbyButton({ if (loggedIn) send(entry.card) else login() }, Modifier.weight(1f)) { Text("나도 명함 주기") }
                        }
                        Text("${page + 1} / ${cards.size} · 위아래로 넘기기", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
