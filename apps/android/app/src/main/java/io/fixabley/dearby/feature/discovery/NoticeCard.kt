package io.fixabley.dearby.feature.discovery

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import io.fixabley.dearby.core.model.Notice
import io.fixabley.dearby.core.model.Organization
import io.fixabley.dearby.core.ui.NoticeFact

@Composable
internal fun NoticeCard(
    notice: Notice, organization: Organization?, contextNames: String, saved: Boolean, position: String,
    save: () -> Unit, showDetail: () -> Unit,
) {
    val currentSave by rememberUpdatedState(save)
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val compact = maxHeight < 500.dp
        OutlinedCard(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(if (compact) 16.dp else 22.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                Column(Modifier.weight(1f).fillMaxWidth()
                    .pointerInput(notice.id) { detectTapGestures(onDoubleTap = { currentSave() }) }
                    .semantics { customActions = listOf(CustomAccessibilityAction("조직 즐겨찾기에 저장") { currentSave(); true }) }
                    .testTag("activity.${notice.id}"),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("공고 샘플", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                        Text(position, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(listOf(notice.categorySummary, contextNames).filter { it.isNotEmpty() }.joinToString(" · "),
                        Modifier.testTag("classification.${notice.id}"),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(notice.title, style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (!compact) {
                        HorizontalDivider()
                        NoticeFact("참여 대상", notice.audience, 2)
                        NoticeFact("신청 마감", notice.application, 2)
                        NoticeFact("활동 장소", notice.location, 2)
                    }
                    if (notice.issues.isNotEmpty()) {
                        Text("확인이 필요한 정보가 있어요", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (organization != null) {
                    Button(onClick = save, modifier = Modifier.fillMaxWidth().testTag("save.${notice.id}")) {
                        Text(if (saved) "저장됨 · ${organization.name}" else "${organization.name} 저장", maxLines = 2)
                    }
                } else {
                    Text("저장할 조직 확인 중", style = MaterialTheme.typography.labelMedium)
                }
                TextButton(onClick = showDetail, modifier = Modifier.fillMaxWidth().testTag("details.${notice.id}")) {
                    Text("공고 정보 · 출처 보기")
                }
            }
        }
    }
}
