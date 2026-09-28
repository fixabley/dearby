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
import androidx.compose.ui.Alignment
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.CardStack
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.widgets.card.cardContent.CardContent
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.activity.contextPicker.ActivityContextPicker
import com.dearby.nativeapp.widgets.activity.contextPicker.ActivityChoiceState

@Composable fun SendPage(cards: List<CardState>, selectedId: String?, recipient: String, busy: Boolean, select: (String) -> Unit, send: (String, String?, String) -> Unit, create: () -> Unit, close: () -> Unit, onContact: (ContactActionState) -> Unit, activities: List<ActivityChoiceState> = emptyList()) {
    var activityId by rememberSaveable { mutableStateOf<String?>(null) }
    var context by rememberSaveable { mutableStateOf("") }
    val pager = rememberPagerState(initialPage = cards.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)) { cards.size }
    LaunchedEffect(pager.currentPage, cards.size) { cards.getOrNull(pager.currentPage)?.let { select(it.id) } }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { TextButton(close, enabled = !busy) { Text("취소") }; Text("내 명함 선택", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.titleMedium); TextButton(create, enabled = !busy) { Text("＋ 새 명함") } }
        Text("$recipient 님에게 보낼 명함", Modifier.align(Alignment.CenterHorizontally), color = Quiet, style = MaterialTheme.typography.bodyMedium)
        Text("어떤 명함을 건넬까요?", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.headlineSmall)
        var expanded by remember { mutableStateOf(false) }
        if (cards.isEmpty()) Text("먼저 내 명함을 만들어 주세요.") else VerticalPager(pager, Modifier.weight(1f).testTag("sendPager"), userScrollEnabled = !busy) { index ->
            CardStack(cards, index, Modifier.fillMaxSize(), onContact, titleBadge = true)
        }
        if (cards.isNotEmpty()) Text("${pager.currentPage + 1} / ${cards.size} · 위아래로 넘기기", Modifier.align(Alignment.CenterHorizontally), color = Quiet, style = MaterialTheme.typography.bodySmall)
        if (expanded && cards.isNotEmpty()) Dialog({ expanded = false }) { CardContent(cards[pager.currentPage], true, { expanded = false }, Modifier.fillMaxWidth().heightIn(max = 650.dp), onContact) }
        TextButton({ expanded = true }, Modifier.align(Alignment.CenterHorizontally), enabled = cards.isNotEmpty() && !busy) { Text("명함 상세보기") }
        var showContext by rememberSaveable { mutableStateOf(false) }
        TextButton({ showContext = !showContext }, enabled = !busy) { Text("교환한 활동 · " + (activities.find { it.id == activityId }?.title ?: context.ifBlank { "선택 안 함" })) }
        if (showContext && !busy) ActivityContextPicker(activities, activityId, context) { id, label -> activityId = id; context = label }
        DearbyButton({ cards.getOrNull(pager.currentPage)?.let { send(it.id, activityId, context) } }, enabled = !busy && cards.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("이 명함 보내기") }
        Text("선택한 명함의 정보만 전달돼요.", style = MaterialTheme.typography.bodySmall)
    }
}
