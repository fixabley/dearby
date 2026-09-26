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

@Composable fun SendPage(cards: List<CardState>, selectedId: String?, recipient: String, busy: Boolean, select: (String) -> Unit, send: (String, String) -> Unit, create: () -> Unit, close: () -> Unit, onContact: (ContactActionState) -> Unit) {
    var context by rememberSaveable { mutableStateOf("") }
    val pager = rememberPagerState(initialPage = cards.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)) { cards.size }
    LaunchedEffect(pager.currentPage, cards.size) { cards.getOrNull(pager.currentPage)?.let { select(it.id) } }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { TextButton(close) { Text("취소") }; TextButton(create) { Text("＋ 새 명함") } }
        Text("$recipient 님에게 보낼 명함", style = MaterialTheme.typography.titleLarge)
        if (cards.isEmpty()) Text("먼저 내 명함을 만들어 주세요.") else VerticalPager(pager, Modifier.weight(1f)) { index ->
            var expanded by remember(cards[index].id) { mutableStateOf(false) }
            Box(Modifier.fillMaxSize()) { CardContent(cards[index], expanded, { expanded = !expanded }, if (expanded) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), onContact) }
        }
        Field("교환한 활동 (선택 사항)", context, { context = it })
        Button({ cards.getOrNull(pager.currentPage)?.let { send(it.id, context) } }, enabled = !busy && cards.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("이 명함 보내기") }
        Text("선택만으로 전송되지 않습니다. 서버 확인 후 전달 완료로 표시합니다.", style = MaterialTheme.typography.bodySmall)
    }
}
