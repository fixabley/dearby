package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/**
 * 받은 명함 목록 한 줄(contract "받은 명함 목록 표시"): 이니셜 아바타, 굵은 이름, 아래 줄에 직무와 소속 칩.
 * 화면이 명함 모델을 값으로 바꿔 넘기고, 줄 전체를 누르면 [open]으로 상세를 연다. 오른쪽 화살표는 두지 않는다.
 */
/**
 * One received card in the wallet list: initial, name, job and chips (조직, then [program] where it was exchanged), and a trailing
 * status [badge] (`맞교환` with [badgeAccent], or `N일 뒤 사라져요`; contract "명함 교환"). [context] is the caption `프로그램 · 블록 · 트랙 · 방` the
 * card was exchanged in (contract "교환 세션 맥락", 2026-10-26), one line under the chips when known. The row's texts form its accessibility label.
 */
@Composable fun ReceivedCardRow(name: String, job: String, open: () -> Unit, modifier: Modifier = Modifier, organization: String? = null,
                                program: String? = null, badge: String? = null, badgeAccent: Boolean = false, context: String? = null) {
    Row(modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClick = open).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f), CircleShape), contentAlignment = Alignment.Center) {
            Text(name.take(1), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
            if (job.isNotBlank() || !organization.isNullOrBlank() || !program.isNullOrBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (job.isNotBlank()) Text(job, Modifier.weight(1f, fill = false), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                if (!organization.isNullOrBlank()) Text(organization, Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
                if (!program.isNullOrBlank()) Text(program, Modifier.background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
            }
            if (!context.isNullOrBlank()) Text(context, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        if (badge != null) ExampleBadge(badge, accent = badgeAccent)
    }
}
