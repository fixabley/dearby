package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.testTag
import io.fixabley.dearby.entities.activitycatalog.model.ActivityCatalog
import io.fixabley.dearby.entities.activitycatalog.model.Notice
import io.fixabley.dearby.shared.ui.NoticeFact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoticeDetailSheet(notice: Notice, catalog: ActivityCatalog?, onDismiss: () -> Unit, onOpenSource: (String) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 24.dp).testTag("notice.detail"),
            contentPadding = PaddingValues(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { Text(notice.title, style = MaterialTheme.typography.headlineSmall) }
            item { Text(notice.summary) }
            if (catalog != null) item { NoticeIdentity(notice, catalog) }
            item { HorizontalDivider() }
            item { NoticeFact("참여 대상", notice.audience) }
            item { NoticeFact("참여 조건", notice.eligibility) }
            item { NoticeFact("신청 기간", notice.application) }
            items(notice.schedule) { NoticeFact("활동 일정", it) }
            item { NoticeFact("활동 장소", notice.location) }
            items(notice.benefits) { NoticeFact("혜택", it) }
            items(notice.issues) { NoticeFact("확인 필요", it) }
            item {
                Text("원문을 검토해 만든 샘플입니다. 현재 모집 여부와 변경된 조건은 원문에서 확인해 주세요.",
                    style = MaterialTheme.typography.bodySmall)
            }
            item {
                Button(onClick = { onOpenSource(notice.sourceUrl) }) { Text("원문 공고 열기") }
            }
        }
    }
}
