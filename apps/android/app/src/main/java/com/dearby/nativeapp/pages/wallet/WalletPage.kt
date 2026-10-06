package com.dearby.nativeapp.pages.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.CardStack
import com.dearby.nativeapp.widgets.card.cardContent.ContactState
import kotlinx.coroutines.launch

data class WalletEntryState(val card: CardState, val reciprocal: Boolean, val activityIds: List<String> = emptyList())
fun walletMatches(entry: WalletEntryState, query: String) = listOf(entry.card.person, entry.card.job, entry.card.title).plus(entry.card.histories.map { it.title }).any { it.contains(query, ignoreCase = true) }

@Composable fun WalletPage(entries: List<WalletEntryState>, query: String, reciprocal: Boolean, changeQuery: (String) -> Unit, changeGroup: (Boolean) -> Unit, detail: (CardState) -> Unit, send: (CardState) -> Unit, contact: (ContactState) -> Unit) {
    val showGroups = entries.any { !it.reciprocal }
    val cards = entries.filter { walletMatches(it, query) && (!showGroups || it.reciprocal == reciprocal) }.map { it.card }
    val pager = rememberPagerState { cards.size }
    val scope = rememberCoroutineScope()
    LaunchedEffect(query, reciprocal) { if (cards.isNotEmpty()) pager.scrollToPage(0) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("받은 명함", Modifier.fillMaxWidth().padding(vertical = 14.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
        TextField(query, changeQuery, Modifier.fillMaxWidth(), placeholder = { Text("이름, 직무, 활동으로 검색", style = MaterialTheme.typography.bodyMedium) }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, shape = MaterialTheme.shapes.large,
            colors = TextFieldDefaults.colors(focusedContainerColor = Soft, unfocusedContainerColor = Soft, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
        if (showGroups) Row { listOf(false to "내 명함을 주지 않은 상대", true to "서로 주고받은 상대").forEach { (value, label) ->
            Column(Modifier.weight(1f)) {
                TextButton({ changeGroup(value) }, Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) { Text("$label ${entries.count { it.reciprocal == value }}", style = MaterialTheme.typography.labelSmall, color = if (value == reciprocal) Teal else Quiet) }
                HorizontalDivider(thickness = if (value == reciprocal) 2.dp else 1.dp, color = if (value == reciprocal) Teal else Line)
            }
        } }
        if (cards.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("표시할 명함이 없어요.", color = Quiet) }
        else {
            val currentCard = cards[pager.currentPage.coerceIn(cards.indices)]
            VerticalPager(pager, Modifier.weight(1f).testTag("walletPager"), pageSpacing = 12.dp) { index -> CardStack(cards, index, Modifier.fillMaxWidth(), contact) }
            Row(Modifier.align(Alignment.CenterHorizontally)) {
                IconButton({ scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }, enabled = pager.currentPage > 0) { Icon(Icons.Outlined.KeyboardArrowUp, "이전 명함", tint = Quiet) }
                IconButton({ scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(cards.lastIndex)) } }, enabled = pager.currentPage < cards.lastIndex) { Icon(Icons.Outlined.KeyboardArrowDown, "다음 명함", tint = Quiet) }
            }
            Text("위아래로 밀어 명함을 넘겨요.  ${pager.currentPage + 1} / ${cards.size}", Modifier.align(Alignment.CenterHorizontally), color = Quiet, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DearbyOutlineButton({ detail(currentCard) }, Modifier.weight(1f)) { Text("명함 상세보기", style = MaterialTheme.typography.labelLarge) }
                if (showGroups && !reciprocal) DearbyButton({ send(currentCard) }, Modifier.weight(1f)) { Text("나도 명함 주기", style = MaterialTheme.typography.labelLarge) }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}
