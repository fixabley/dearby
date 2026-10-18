package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
 * 명함 제작(contract "템플릿"): 본문을 렌더링 화면에서 바로 꾸미고, 프로필 데이터를 토큰으로 넣고, 공개할 연락처·이력을 고른다.
 * 로그인·발행·오류는 화면이 맡고, 이 위젯은 값과 콜백만 받는다. 내용이 바뀌면 "원래대로"가 나타난다.
 * [webTemplate]이 null이 아니면 웹 버전이 켜진 것이고, `모바일 | 웹` 전환으로 어느 템플릿을 고치는지 고른다(contract "웹 버전").
 */
@Composable fun CardComposer(
    cardTitle: String, onCardTitleChange: (String) -> Unit, template: String, onTemplateChange: (String) -> Unit, tokens: List<TemplateToken>,
    preview: String, contacts: List<ComposerContact>, onContactPublic: (String, Boolean) -> Unit, histories: List<ComposerHistory>, onHistoryPublic: (String, Boolean) -> Unit,
    changed: Boolean, onRevert: () -> Unit, onPublish: () -> Unit, modifier: Modifier = Modifier, publishing: Boolean = false, errorMessage: String? = null,
    webTemplate: String? = null, onWebTemplateChange: (String?) -> Unit = {}, webPreview: String? = null,
) {
    var insertOpen by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    // Which version the editor, token insert and preview target (contract "웹 버전"); falls back to 모바일 when the web version is off.
    var editingWeb by remember { mutableStateOf(false) }
    val webOn = webTemplate != null
    val onWeb = webOn && editingWeb
    val activeTemplate = if (onWeb) webTemplate.orEmpty() else template
    val onActiveChange: (String) -> Unit = if (onWeb) ({ onWebTemplateChange(it) }) else onTemplateChange
    val activePreview = if (onWeb) webPreview.orEmpty() else preview
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Section("명함") { Labeled("명함 이름 (선택)") { DearbyInlineField("명함 이름", cardTitle, onCardTitleChange, editing = true, placeholder = "비워 두면 '내 명함'으로 저장해요") } }
            Section("꾸미기") {
                Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("웹 버전 따로 쓰기", style = MaterialTheme.typography.bodyMedium)
                        Text("웹에서 넓은 화면(640px 초과)일 때 보이는 내용이에요. 앱은 항상 모바일 버전을 보여요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
                    }
                    // Turning on starts the web version from the mobile template; turning off drops it (원래대로 restores).
                    Switch(webOn, { on -> editingWeb = on; onWebTemplateChange(if (on) template else null) }, Modifier.semantics { contentDescription = "웹 버전 따로 쓰기" })
                }
                if (webOn) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("모바일", "웹").forEachIndexed { index, label ->
                        SegmentedButton(selected = onWeb == (index == 1), onClick = { editingWeb = index == 1 }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
                    }
                }
                TemplateEditor(activeTemplate, tokens, onActiveChange)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        TextButton({ insertOpen = true }, contentPadding = PaddingValues(0.dp)) { Text("데이터 넣기") }
                        DropdownMenu(insertOpen, { insertOpen = false }) {
                            tokens.forEach { token ->
                                DropdownMenuItem({ Text(token.label + if (token.value.isNotEmpty()) " · ${token.value}" else "") },
                                    { insertOpen = false; onActiveChange((if (activeTemplate.isEmpty() || activeTemplate.endsWith("\n")) activeTemplate else activeTemplate + "\n") + "{{${token.id}}}") })
                            }
                        }
                    }
                    TextButton({ showPreview = !showPreview }, contentPadding = PaddingValues(0.dp)) { Text(if (showPreview) "미리보기 닫기" else "미리보기") }
                }
                if (showPreview) OutlinedCard(Modifier.fillMaxWidth()) { MarkdownBody(activePreview, Modifier.padding(12.dp)) }
            }
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
