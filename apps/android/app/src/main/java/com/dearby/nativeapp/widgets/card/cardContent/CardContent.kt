package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/**
 * 명함 카드 면(contract "명함 표현", 2026-10-18): 흰 바탕·모서리 20·구분선 테두리·안쪽 여백 20. 머리(아바타·이름·직무 칩·소속) →
 * 소개 마크다운 → 연락처 → 활동 이력. 카드는 자기 높이만큼 펼쳐지고 스크롤은 담는 화면이 맡는다. 행동 버튼은 호출자가 카드 바깥에 둔다.
 */
@Composable fun CardContent(state: CardState, modifier: Modifier = Modifier, onContact: (ContactState) -> Unit) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Header(state)
            if (state.introduction.isNotBlank()) MarkdownBody(state.introduction)
            Section("연락처") {
                if (state.contacts.isEmpty()) Empty("공개된 연락처가 없어요")
                else state.contacts.forEach { ContactRow(it) { onContact(it) } }
            }
            Section("활동 이력") {
                if (state.histories.isEmpty()) Empty("공개된 활동 이력이 없어요")
                else state.histories.forEach { HistoryRow(it) }
            }
        }
    }
}

@Composable private fun Header(state: CardState) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        InitialAvatar(state.person, 56.dp)
        Text(state.person, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
        // `job`은 서버가 승인된 직무 이름을 " · "로 이어 준 문자열이라 항목마다 칩으로 되돌린다.
        val jobs = state.job.split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
        if (jobs.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            jobs.forEach { ExampleBadge(it) }
        }
        state.organization?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, Modifier.padding(bottom = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable private fun Empty(text: String) = Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)

/** 행 전체를 눌러 연다; 열 수 있는 값이면 오른쪽에 외부 링크 아이콘을 둔다. */
@Composable private fun ContactRow(contact: ContactState, open: () -> Unit) {
    val openable = contact.target != null
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(enabled = openable, role = Role.Button, onClick = open).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ContactSymbol(contact.kind, Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(contact.label.ifBlank { contactKindName(contact.kind) }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text(contact.value, style = MaterialTheme.typography.bodyMedium)
        }
        if (openable) Icon(Icons.AutoMirrored.Outlined.OpenInNew, "열기", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun HistoryRow(history: CardHistoryState) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(history.date, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(history.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (history.role.isNotBlank()) Text(history.role, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            // 미확인: no admin-verified evidence yet.
            ExampleBadge(if (history.verified) "확인됨" else "미확인", accent = history.verified)
        }
        if (history.description.isNotBlank()) Text(history.description, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
