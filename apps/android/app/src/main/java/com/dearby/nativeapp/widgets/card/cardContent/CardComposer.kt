package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/** A contact the card can show; [locked] when the template names it (then it is always public). */
data class ComposerContact(val id: String, val label: String, val value: String, val isPublic: Boolean, val locked: Boolean)
/** A history the card can show; [reason] explains why it cannot be chosen (not approved yet / rejected). */
data class ComposerHistory(val id: String, val title: String, val detail: String, val isPublic: Boolean, val reason: String?)

/**
 * 명함 제작(contract "템플릿", 2026-10-18 앱 안 꾸미기 삭제): 명함 이름을 적고 공개할 연락처·이력을 고른다. 본문은 앱 내장 기본 템플릿을 채운
 * 결과를 읽기 전용 미리보기로 보인다. 로그인·발행·오류는 화면이 맡고, 이 위젯은 값과 콜백만 받는다. 내용이 바뀌면 "원래대로"가 나타난다.
 * 명함 이름은 라벨을 위에 쌓은 큰 입력 `DearbyField`(contract "폼 라벨·입력 크기", 2026-10-20), 공개 스위치는 `ListItem` 줄 전체가 토글이다(contract "폼은 플랫폼 기본 컴포넌트").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun CardComposer(
    cardTitle: String, onCardTitleChange: (String) -> Unit, preview: String,
    contacts: List<ComposerContact>, onContactPublic: (String, Boolean) -> Unit, histories: List<ComposerHistory>, onHistoryPublic: (String, Boolean) -> Unit,
    changed: Boolean, onRevert: () -> Unit, onPublish: () -> Unit, modifier: Modifier = Modifier, publishing: Boolean = false, errorMessage: String? = null,
) {
    Column(modifier.fillMaxSize().dismissKeyboardOnTap()) {
        Column(Modifier.weight(1f).imeNestedScroll().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Section("명함") {
                DearbyField("명함 이름", cardTitle, onCardTitleChange, hint = "선택이에요. 비워 두면 '내 명함'으로 저장해요.")
            }
            // The preview sits on the page background, no card around it (contract "카드는 섹션을 나눌 때만").
            Section("미리보기") { MarkdownBody(preview) }
            Section("공개할 연락처") {
                if (contacts.isEmpty()) Text("프로필에 연락처가 없어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                contacts.forEach { contact ->
                    ToggleRow("${contact.label} · ${contact.value}", if (contact.locked) "본문에 넣어서 공개돼요" else null, contact.isPublic, enabled = !contact.locked, description = "${contact.label} 공개") { onContactPublic(contact.id, it) }
                }
            }
            Section("공개할 활동 이력") {
                if (histories.isEmpty()) Text("프로필에 이력이 없어요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                histories.forEach { history ->
                    ToggleRow(history.title, listOfNotNull(history.detail.takeIf { it.isNotBlank() }, history.reason).joinToString("\n").ifEmpty { null }, history.isPublic,
                        enabled = history.reason == null, description = "${history.title} 공개", error = history.reason != null) { onHistoryPublic(history.id, it) }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHighest)
        Column(Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            errorMessage?.let { Text(it, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (changed) DearbyOutlineButton(onRevert, Modifier.weight(1f), enabled = !publishing) { Text("원래대로") }
                DearbyButton(onPublish, Modifier.weight(1f).semantics { if (publishing) stateDescription = "발행 중" }, enabled = !publishing) {
                    if (publishing) CircularProgressIndicator(Modifier.size(20.dp), color = LocalContentColor.current, strokeWidth = 2.dp) else Text("명함 발행")
                }
            }
            Text("발행한 명함은 바뀌지 않아요. 고치면 새 명함으로 발행돼요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
    content()
}

/** One `ListItem` whose whole row toggles the `Switch` (contract "폼은 플랫폼 기본 컴포넌트"); [error] colours the supporting line. */
@Composable private fun ToggleRow(title: String, detail: String?, checked: Boolean, enabled: Boolean, description: String, error: Boolean = false, onChecked: (Boolean) -> Unit) = ListItem(
    { Text(title) }, Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChecked),
    supportingContent = detail?.let { { Text(it, color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) } },
    trailingContent = { Switch(checked, null, Modifier.semantics { contentDescription = description }, enabled = enabled) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
