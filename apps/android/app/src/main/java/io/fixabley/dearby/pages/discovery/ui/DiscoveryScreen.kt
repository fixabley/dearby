package io.fixabley.dearby.pages.discovery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import io.fixabley.dearby.widgets.notice.noticecard.NoticeCardState
import io.fixabley.dearby.widgets.notice.noticecard.NoticeCard

@Composable
internal fun DiscoveryScreen(snapshotDate: String, cards: List<NoticeCardState>, onSave: (String) -> String, showDetail: (String) -> Unit) {
    var feedback by remember { mutableStateOf("") }
    val pager = rememberPagerState(pageCount = { cards.size })
    Column(Modifier.fillMaxSize()) {
        Text("검토한 공고 샘플 · ${snapshotDate}",
            Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (cards.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("표시할 공고가 없어요") }
        } else {
            VerticalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag("discovery.pager"), key = { cards[it].id }) { index ->
                val notice = cards[index]
                NoticeCard(notice, "${index + 1} / ${cards.size}",
                    { feedback = onSave(notice.id) }, { showDetail(notice.id) })
            }
        }
        Text(feedback.ifEmpty { "위아래로 넘기기 · 더블탭으로 조직 저장" },
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).testTag("discovery.feedback"),
            style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
}
