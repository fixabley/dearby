package com.dearby.nativeapp.pages.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.ReceivedCardRow

/** A saved card and the activity IDs its shares carried. */
data class WalletEntryState(val card: CardState, val activityIds: List<String> = emptyList())
enum class WalletPhase { SIGNED_OUT, LOADING, LOADED, FAILED }
/** 받은 명함: the account's wallet; [activities] are (id, title) in the order they were first saved. */
data class WalletState(val phase: WalletPhase = WalletPhase.LOADING, val entries: List<WalletEntryState> = emptyList(), val activities: List<Pair<String, String>> = emptyList())

@Composable fun WalletPage(state: WalletState, query: String, collapsed: Set<String>, changeQuery: (String) -> Unit, toggle: (String) -> Unit,
                           open: (CardState) -> Unit, signIn: () -> Unit, retry: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("받은 명함")
        when (state.phase) {
            WalletPhase.SIGNED_OUT -> Notice("받은 명함은 계정에 저장돼요", "로그인하면 저장한 명함을 볼 수 있어요. QR 탭에서 명함을 찍어 저장할 수 있어요.") {
                DearbyButton(signIn, Modifier.fillMaxWidth()) { Text("로그인") }
            }
            WalletPhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Teal) }
            WalletPhase.FAILED -> Notice("받은 명함을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.") {
                DearbyOutlineButton(retry, Modifier.fillMaxWidth()) { Text("다시 시도") }
            }
            WalletPhase.LOADED -> if (state.entries.isEmpty()) Notice("아직 받은 명함이 없어요", "QR 탭에서 명함을 찍고 '받은 명함에 저장'을 눌러 보세요.") {} else {
                val groups = walletGroups(state.entries, state.activities, query, collapsed)
                DearbySearchField(query, changeQuery, "이름, 직무, 활동으로 검색", Modifier.padding(horizontal = 20.dp))
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)) {
                    if (groups.isEmpty()) item { Notice("찾는 명함이 없어요", "이름이나 활동으로 다시 검색해 주세요.") {} }
                    groups.forEach { group ->
                        item(key = "header-${group.id}") { DearbySectionHeader(group.title, group.entries.size, expanded = group.expanded, onToggle = { toggle(group.id) }) }
                        if (group.expanded) items(group.entries, key = group::key) { entry ->
                            ReceivedCardRow(entry.card.person, entry.card.job, { open(entry.card) })
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun Notice(title: String, detail: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text(detail, color = Quiet, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        action()
    }
}
