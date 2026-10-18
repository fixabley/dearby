package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/** A contact the card can show; [locked] when the template names it (then it is always public). */
data class ComposerContact(val id: String, val label: String, val value: String, val isPublic: Boolean, val locked: Boolean)
/** A history the card can show; [reason] explains why it cannot be chosen (not approved yet / rejected). */
data class ComposerHistory(val id: String, val title: String, val detail: String, val isPublic: Boolean, val reason: String?)

/**
 * 명함 제작(contract "템플릿", 2026-10-18 앱 안 꾸미기 삭제): 명함 이름을 적고 공개할 연락처·이력을 고른다. 본문은 앱 내장 기본 템플릿을 채운
 * 결과를 읽기 전용 미리보기로 보인다. 로그인·발행·오류는 화면이 맡고, 이 위젯은 값과 콜백만 받는다. 내용이 바뀌면 "원래대로"가 나타난다.
 */
@Composable fun CardComposer(
    cardTitle: String, onCardTitleChange: (String) -> Unit, preview: String,
    contacts: List<ComposerContact>, onContactPublic: (String, Boolean) -> Unit, histories: List<ComposerHistory>, onHistoryPublic: (String, Boolean) -> Unit,
    changed: Boolean, onRevert: () -> Unit, onPublish: () -> Unit, modifier: Modifier = Modifier, publishing: Boolean = false, errorMessage: String? = null,
) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Section("명함") { Labeled("명함 이름 (선택)") { DearbyInlineField("명함 이름", cardTitle, onCardTitleChange, editing = true, placeholder = "비워 두면 '내 명함'으로 저장해요") } }
            Section("미리보기") { OutlinedCard(Modifier.fillMaxWidth()) { MarkdownBody(preview, Modifier.padding(12.dp)) } }
            Section("공개할 연락처") {
                if (contacts.isEmpty()) Text("프로필에 연락처가 없어요.", color = Quiet, style = MaterialTheme.typography.bodyMedium)
                contacts.forEach { contact ->
                    Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${contact.label} · ${contact.value}", style = MaterialTheme.typography.bodyMedium)
                            if (contact.locked) Text("본문에 넣어서 공개돼요", color = Quiet, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(contact.isPublic, { onContactPublic(contact.id, it) }, Modifier.semantics { contentDescription = "${contact.label} 공개" }, enabled = !contact.locked)
                    }
                }
            }
            Section("공개할 활동 이력") {
                if (histories.isEmpty()) Text("프로필에 이력이 없어요.", color = Quiet, style = MaterialTheme.typography.bodyMedium)
                histories.forEach { history ->
                    Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(history.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            if (history.detail.isNotBlank()) Text(history.detail, color = Quiet, style = MaterialTheme.typography.bodySmall)
                            history.reason?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }
                        }
                        Switch(history.isPublic, { onHistoryPublic(history.id, it) }, Modifier.semantics { contentDescription = "${history.title} 공개" }, enabled = history.reason == null)
                    }
                }
            }
        }
        HorizontalDivider(color = Line)
        Column(Modifier.background(Color.White).padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            errorMessage?.let { Text(it, Modifier.fillMaxWidth(), color = Danger, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (changed) DearbyOutlineButton(onRevert, Modifier.weight(1f), enabled = !publishing) { Text("원래대로") }
                DearbyButton(onPublish, Modifier.weight(1f).semantics { if (publishing) stateDescription = "발행 중" }, enabled = !publishing) {
                    if (publishing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text("명함 발행", style = MaterialTheme.typography.titleMedium)
                }
            }
            Text("발행한 명함은 바뀌지 않아요. 고치면 새 명함으로 발행돼요.", color = Quiet, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
    content()
}

@Composable private fun Labeled(label: String, field: @Composable () -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(label, Modifier.clearAndSetSemantics {}, color = Quiet, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
    field()
}
