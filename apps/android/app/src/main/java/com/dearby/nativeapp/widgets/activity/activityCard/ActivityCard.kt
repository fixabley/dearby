package com.dearby.nativeapp.widgets.activity.activityCard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/**
 * 펼친 프로그램 카드 안의 활동 카드(contract "발견 카드 리뉴얼", Material 3 `Card`): 로고 없이 왼쪽 위 활동명, 오른쪽 위 모집 상태 `SuggestionChip`, 그 아래 분야 [fieldTag] 1개와 성격 [traitTags]의 아이콘+글자 칩 한 줄(넘치면 `+n`, 없으면 생략), `모집`·`활동`·`장소` 세 줄.
 * [status]는 `application` 판정(`모집 중`·`모집 예정`·`모집 마감`·`모집 여부 확인 필요`)이며 칩은 청록 채움(`TealFill`·흰 글자) / 민트 / 회색 `마감`(판정 불가는 회색 `확인 필요`)이다.
 * [recruitment]이 null이면 `미정`, [place]가 null이면 `장소` 줄을 생략한다. 모집이 지나지 않았으면([recruiting]) `모집` 값은 청록 굵은 글씨에 민트 바탕이다.
 * 카드(또는 상태 칩)를 누르면 [open](상세 이동). 접근성에서는 카드 하나로 읽히고 상태가 상태 설명으로 붙는다.
 */
@Composable fun ActivityCard(
    title: String, status: String, recruiting: Boolean, recruitment: String?, period: String, place: String?, open: () -> Unit, modifier: Modifier = Modifier,
    fieldTag: TagLabel? = null, traitTags: List<TagLabel> = emptyList(),
    /** 즐겨찾기 하트·`+` (contract "즐겨찾기(하트)·새 모집 푸시 알림"), at the end of the tag line; their own taps, not the card's. */
    actions: (@Composable () -> Unit)? = null,
) {
    val (label, container, content) = when (status) {
        "모집 중" -> Triple(status, TealFill, Color.White)
        "모집 예정" -> Triple(status, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
        "모집 마감" -> Triple("마감", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Triple("확인 필요", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Card(open, modifier.fillMaxWidth().semantics { stateDescription = label }, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Text(title, Modifier.weight(1f).padding(top = 4.dp), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // The chip's semantics fold into the card (state description above); tapping it opens the detail like the card.
                SuggestionChip(open, { Text(label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium) }, Modifier.height(28.dp).clearAndSetSemantics {},
                    colors = SuggestionChipDefaults.suggestionChipColors(containerColor = container, labelColor = content), border = null)
            }
            // 분야 1개 + 성격, the same chip line as the program card's (contract "태그 아이콘+라벨·분야 1개").
            if (actions == null) TagChipRow(fieldTag, traitTags)
            else Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.weight(1f)) { TagChipRow(fieldTag, traitTags) }; actions() }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                ValueRow("모집", recruitment ?: "미정", emphasised = recruiting)
                ValueRow("활동", period.ifBlank { "미정" })
                if (place != null) ValueRow("장소", place)
            }
        }
    }
}

/** 라벨 36dp + 값. [emphasised]는 모집이 지나지 않은 `모집` 줄: 청록 굵은 값, 줄 전체에 민트 바탕(모서리 8). */
@Composable private fun ValueRow(label: String, value: String, emphasised: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().then(if (emphasised) Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 3.dp) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, Modifier.width(36.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Text(value, Modifier.weight(1f), color = if (emphasised) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (emphasised) FontWeight.Bold else null, style = MaterialTheme.typography.bodySmall)
    }
}
