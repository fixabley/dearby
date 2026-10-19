package com.dearby.nativeapp.pages.wallet

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.ReceivedCardRow
import kotlinx.coroutines.launch

/** Index rails shorter than this are noise (contract "받은 명함 목록 표시": 묶음이 6개 이하이면 숨긴다). */
private const val RAIL_MIN_SECTIONS = 7

@OptIn(ExperimentalFoundationApi::class)
@Composable fun WalletPage(state: WalletState, query: String, changeQuery: (String) -> Unit, open: (ReceivedCard) -> Unit, signIn: () -> Unit, retry: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("받은 명함")
        when (state.phase) {
            WalletPhase.SIGNED_OUT -> Notice("받은 명함은 계정에 저장돼요", "로그인하면 저장한 명함을 볼 수 있어요.") {
                DearbyButton(signIn, Modifier.fillMaxWidth()) { Text("로그인") }
            }
            WalletPhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
            WalletPhase.FAILED -> Notice("받은 명함을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.") {
                DearbyOutlineButton(retry, Modifier.fillMaxWidth()) { Text("다시 시도") }
            }
            WalletPhase.LOADED -> if (state.cards.isEmpty()) Notice("아직 받은 명함이 없어요", "상대의 명함 QR을 카메라로 읽고 ‘Dearby 앱에 저장’을 누르면 여기에 모여요.") {} else {
                val sections = walletSections(filterWallet(state.cards, query))
                DearbySearchField(query, changeQuery, "이름, 직무로 검색", Modifier.padding(horizontal = 20.dp))
                if (sections.isEmpty()) Notice("찾는 명함이 없어요", "이름이나 직무로 다시 검색해 주세요.") {}
                else IndexedList(sections, open)
            }
        }
    }
}

/** Sections with sticky letter headers; a right-edge rail jumps to a section when the list is long enough to need it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable private fun IndexedList(sections: List<WalletSection>, open: (ReceivedCard) -> Unit) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    // Item index of each section header: headers and rows are laid out in order, one item each.
    val headerIndex = remember(sections) {
        var index = 0
        sections.map { section -> index.also { index += 1 + section.cards.size } }
    }
    fun jump(section: Int) = scope.launch {
        val target = headerIndex[section.coerceIn(headerIndex.indices)]
        if (reduceMotion) listState.scrollToItem(target) else listState.animateScrollToItem(target)
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), listState, contentPadding = PaddingValues(start = 20.dp, end = if (sections.size >= RAIL_MIN_SECTIONS) 36.dp else 20.dp, bottom = 12.dp)) {
            sections.forEach { section ->
                stickyHeader(key = "header-${section.key}") {
                    Text(section.key, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 8.dp, vertical = 4.dp).semantics { heading() },
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                }
                items(section.cards, key = { it.id }) { received ->
                    ReceivedCardRow(received.card.person, received.card.job, { open(received) }, organization = received.card.organization,
                        program = received.program, badge = received.badge, badgeAccent = received.reciprocal)
                }
            }
        }
        if (sections.size >= RAIL_MIN_SECTIONS) IndexRail(sections.map { it.key }, Modifier.align(Alignment.CenterEnd), ::jump)
    }
}

/** Vertical letters at the right edge; tap or drag selects the section under the finger. */
@Composable private fun IndexRail(keys: List<String>, modifier: Modifier = Modifier, jump: (Int) -> Unit) {
    var height by remember { mutableIntStateOf(0) }
    fun sectionAt(y: Float): Int = if (height <= 0) 0 else (y / height * keys.size).toInt().coerceIn(0, keys.size - 1)
    Column(
        modifier.padding(end = 4.dp).onSizeChanged { height = it.height }
            .pointerInput(keys) { detectTapGestures { offset -> jump(sectionAt(offset.y)) } }
            .pointerInput(keys) { detectDragGestures { change, _ -> change.consume(); jump(sectionAt(change.position.y)) } },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        keys.forEach { key ->
            Text(key, Modifier.size(width = 24.dp, height = 18.dp).semantics { role = Role.Button; contentDescription = "${key}으로 이동" },
                color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun Notice(title: String, detail: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        action()
    }
}
