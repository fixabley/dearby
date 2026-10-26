package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.dearby.nativeapp.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/**
 * 명함 카드 면(contract "명함 표현", 2026-10-18): 흰 바탕·모서리 20·구분선 테두리·안쪽 여백 20. 머리(아바타·이름과 소개 링크·직무 칩·소속) →
 * 연락처 → 활동 이력(소개 글은 2026-10-26 폐지). 카드는 자기 높이만큼 펼쳐지고 스크롤은 담는 화면이 맡는다. 행동 버튼은 호출자가 카드 바깥에 둔다.
 * [body] false는 머리와 연락처만 그린다 — 내 프로필 보기 모드가 이력을 카드 아래에 같은 [CardHistoryRow]로 따로 둔다.
 */
@Composable fun CardContent(state: CardState, modifier: Modifier = Modifier, body: Boolean = true, onContact: (ContactState) -> Unit) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Header(state, onContact)
            Section("연락처") {
                if (state.contacts.isEmpty()) Empty("공개된 연락처가 없어요")
                else state.contacts.forEach { ContactRow(it) { onContact(it) } }
            }
            if (body) Section("활동 이력") {
                if (state.histories.isEmpty()) Empty("공개된 활동 이력이 없어요")
                else state.histories.forEach { CardHistoryRow(it) }
            }
        }
    }
}

@Composable private fun Header(state: CardState, onContact: (ContactState) -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PersonAvatar(state.person, state.photoUrl, 56.dp)
        // 소개 링크 sits right after the name and wraps under it when the line is full (contract "소개 링크·소개 글 폐지").
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Text(state.person, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
            state.introLinkContact?.let { link ->
                AssistChip({ onContact(link) }, { Text("소개") }, Modifier.semantics { contentDescription = "소개 링크 열기" },
                    leadingIcon = { Icon(if (state.introLinkIsNotion) ImageVector.vectorResource(R.drawable.ic_brand_notion) else Icons.Rounded.Link, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.primary, leadingIconContentColor = MaterialTheme.colorScheme.primary))
            }
        }
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

/**
 * 왼쪽 종류 아이콘(24 보조색, 브랜드는 단색 마크; 접근성 이름은 종류)과 값만(contract "연락처 행은 라벨 대신 왼쪽 아이콘", 2026-10-20). 행 전체를 눌러 열거나(외부 링크 아이콘)
 * 카카오톡 ID를 복사한다(복사 아이콘; 2026-10-26 "연락처 아이콘·바로가기").
 */
@Composable private fun ContactRow(contact: ContactState, open: () -> Unit) {
    val opens = contact.target != null
    val copies = contact.copyText != null
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(enabled = opens || copies, onClickLabel = when { copies -> "ID를 복사해요"; contact.kind == "phone" -> "전화를 걸어요"; contact.kind == "email" -> "메일을 보내요"; else -> "링크를 열어요" }, role = Role.Button, onClick = open).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ContactKindIcon(contact.kind, contact.label.ifBlank { contactKindName(contact.kind) })
        Text(contact.shown, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (opens) Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        else if (copies) Icon(Icons.Rounded.ContentCopy, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 명함 이력 한 줄: 기간, 조직 · 프로그램 · 활동, 역할과 확인됨/미확인(내 프로필은 승인 상태도), 설명 한 줄. 내 프로필 보기 모드도 이것을 쓴다. */
@Composable fun CardHistoryRow(history: CardHistoryState) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(history.date, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(history.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (history.role.isNotBlank()) Text(history.role, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            // 미확인: no admin-verified evidence yet.
            ExampleBadge(if (history.verified) "확인됨" else "미확인", accent = history.verified)
            history.approval?.let { ExampleBadge(it) }
        }
        if (history.description.isNotBlank()) Text(history.description, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
