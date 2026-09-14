package io.fixabley.dearby.feature.noticedetail

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import io.fixabley.dearby.entities.activitycatalog.model.ActivityCatalog
import io.fixabley.dearby.entities.activitycatalog.model.Notice
import io.fixabley.dearby.shared.ui.NoticeFact

@Composable
internal fun NoticeIdentity(notice: Notice, catalog: ActivityCatalog) {
    Surface(modifier = Modifier.fillMaxWidth().testTag("identity.${notice.id}"),
        shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            catalog.organization(notice.organizationId)?.let { target ->
                NoticeFact("관심 조직", target.name)
                val ancestors = catalog.organizationPath(target.id).dropLast(1)
                if (ancestors.isNotEmpty()) {
                    NoticeFact("상위 조직", ancestors.joinToString(" › ") { it.name })
                }
                Text("이 공고에서 관심 표시하면 ${target.name}이 저장돼요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            NoticeFact("활동 분류", notice.categorySummary)
            notice.contexts.forEach { context ->
                catalog.organization(context.organizationId)?.let { NoticeFact(context.label, it.name) }
            }
            notice.edition?.let { NoticeFact("회차", "제${it}회") }
        }
    }
}
