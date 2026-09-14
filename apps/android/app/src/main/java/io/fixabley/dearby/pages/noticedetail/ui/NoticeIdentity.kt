package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import io.fixabley.dearby.entities.noticecatalog.model.NoticeDetail
import io.fixabley.dearby.shared.ui.NoticeFact

@Composable
internal fun NoticeIdentity(notice: NoticeDetail) {
    Surface(modifier = Modifier.fillMaxWidth().testTag("identity.${notice.id}"),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            notice.organizationPath.lastOrNull { it.id == notice.organizationId }?.let { target ->
                NoticeFact("관심 조직", target.name)
                val ancestors = notice.organizationPath.dropLast(1)
                if (ancestors.isNotEmpty()) {
                    NoticeFact("상위 조직", ancestors.joinToString(" › ") { it.name })
                }
                Text("이 공고에서 관심 표시하면 ${target.name}이 저장돼요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            NoticeFact("활동 분류", notice.categorySummary)
            notice.contexts.forEach { context ->
                context.name?.let { NoticeFact(context.label, it) }
            }
            notice.edition?.let { NoticeFact("회차", "제${it}회") }
        }
    }
}
