package io.fixabley.dearby.pages.discovery.ui

import androidx.compose.foundation.layout.*
import io.fixabley.dearby.entities.notice.model.NoticeVenue
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.fixabley.dearby.shared.ui.theme.Spacing
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.fixabley.dearby.shared.ui.StatusPanel
import androidx.compose.ui.platform.testTag
import io.fixabley.dearby.widgets.notice.noticecard.NoticeCardState
import io.fixabley.dearby.widgets.notice.noticecard.NoticeCard

@Composable
internal fun DiscoveryScreen(snapshotDate: String, cards: List<NoticeCardState>, onSave: (String) -> String, showDetail: (String) -> Unit, onOpenMap: (NoticeVenue) -> Unit = {}) {
    var feedback by remember { mutableStateOf("") }
    val pager = rememberPagerState(pageCount = { cards.size })
    Column(Modifier.fillMaxSize()) {
        Text("검토한 공고 샘플 · ${snapshotDate}",
            Modifier.padding(horizontal = Spacing.extraLarge, vertical = Spacing.small),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (cards.isEmpty()) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.extraLarge),
                verticalArrangement = Arrangement.Center) { StatusPanel("표시할 공고가 없어요") }
        } else {
            VerticalPager(pager, Modifier.weight(1f).fillMaxWidth().testTag("discovery.pager"), key = { cards[it].id }) { index ->
                val notice = cards[index]
                NoticeCard(notice, "${index + 1} / ${cards.size}",
                    { feedback = onSave(notice.id) }, { showDetail(notice.id) }, onOpenMap)
            }
        }
        Text(feedback.ifEmpty { "위아래로 넘기기 · 더블탭으로 조직 저장" },
            Modifier.fillMaxWidth().padding(horizontal = Spacing.extraLarge, vertical = Spacing.small).testTag("discovery.feedback").semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.labelSmall, maxLines = 2)
    }
}
