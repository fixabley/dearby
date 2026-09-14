package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import io.fixabley.dearby.pages.noticedetail.model.NoticeDetailState
import io.fixabley.dearby.shared.ui.InformationRow
import io.fixabley.dearby.shared.ui.ContentSection

@Composable
internal fun NoticeIdentity(notice: NoticeDetailState) {
    ContentSection(Modifier.testTag("identity.${notice.id}")) {
            notice.organizationName?.let { name ->
                InformationRow("관심 조직", name)
                val ancestors = notice.ancestorNames
                if (ancestors.isNotEmpty()) {
                    InformationRow("상위 조직", ancestors.joinToString(" › "))
                }
                Text("이 공고에서 관심 표시하면 ${name}이 저장돼요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            InformationRow("활동 분류", notice.categorySummary)
            notice.contexts.forEach { context ->
                context.name?.let { InformationRow(context.label, it) }
            }
            notice.edition?.let { InformationRow("회차", "제${it}회") }
    }
}
