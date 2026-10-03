package com.dearby.nativeapp.pages.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.CardStack
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState

@Composable fun SendPage(cards: List<CardState>, recipient: String, send: (String) -> Unit, create: () -> Unit, close: () -> Unit, detail: (CardState) -> Unit, contact: (ContactState) -> Unit) {
    val pager = rememberPagerState { cards.size }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader("내 명함 선택", close) { TextButton(create) { Text("＋ 새 명함") } }
        Text("${recipient}님에게 보낼 명함", Modifier.align(Alignment.CenterHorizontally), color = Quiet, style = MaterialTheme.typography.bodyMedium)
        Text("어떤 명함을 건넬까요?", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.headlineSmall)
        Text("위아래로 밀어 명함을 골라주세요.", Modifier.align(Alignment.CenterHorizontally), color = Quiet, style = MaterialTheme.typography.bodySmall)
        VerticalPager(pager, Modifier.weight(1f).testTag("sendPager")) { index -> CardStack(cards, index, Modifier.fillMaxWidth(), contact, titleBadge = true) }
        Text("${pager.currentPage + 1} / ${cards.size}", Modifier.align(Alignment.CenterHorizontally), color = Quiet, style = MaterialTheme.typography.bodySmall)
        TextButton({ detail(cards[pager.currentPage]) }, Modifier.align(Alignment.CenterHorizontally)) { Text("명함 상세보기") }
        Text("선택한 명함을 건네는 화면 예시예요.", Modifier.align(Alignment.CenterHorizontally), color = Quiet, style = MaterialTheme.typography.bodySmall)
        DearbyButton({ send(cards[pager.currentPage].id) }, Modifier.fillMaxWidth()) { Text("이 명함 보내기") }
    }
}
