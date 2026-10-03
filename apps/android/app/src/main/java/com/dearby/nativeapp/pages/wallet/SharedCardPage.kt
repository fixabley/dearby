package com.dearby.nativeapp.pages.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState

@Composable fun SharedCardPage(card: CardState, saved: Boolean, back: () -> Unit, save: () -> Unit, send: () -> Unit, contact: (ContactState) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("공유 카드", back) { DearbyLogo(Modifier.padding(end = 16.dp)) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            PersonHeader(card.person, "", card.introduction)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                card.contacts.forEach { item -> TextButton({ contact(item) }) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { ContactSymbol(item.kind, Modifier.size(30.dp)); Text(item.label, style = MaterialTheme.typography.bodySmall) } } }
            }
            HorizontalDivider()
            Row { Text("활동 이력", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall); Text("직접 작성", color = Quiet, style = MaterialTheme.typography.bodySmall) }
            Column { card.histories.forEachIndexed { index, history -> TimelineEntry(history.date, history.title, history.role, index == card.histories.lastIndex) } }
        }
        HorizontalDivider()
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("예시 명함 · 이번 실행 중에만 저장돼요.", Modifier.align(Alignment.CenterHorizontally), color = Quiet, style = MaterialTheme.typography.bodySmall)
            DearbyButton(save, Modifier.fillMaxWidth(), enabled = !saved) { Text(if (saved) "카드 저장됨" else "카드 저장") }
            DearbyOutlineButton(send, Modifier.fillMaxWidth()) { Text("나도 카드 주기") }
        }
    }
}
