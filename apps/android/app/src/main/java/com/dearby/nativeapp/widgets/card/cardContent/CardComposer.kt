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

/** 명함 제작에서 공개 여부를 고르는 연락처 한 줄의 값. */
data class CardComposerContact(val id: String, val kind: String, val label: String, val value: String, val isPublic: Boolean)

/** 명함 제작에서 공개 여부를 고르는 활동 이력 한 줄의 값. */
data class CardComposerHistory(val id: String, val title: String, val detail: String, val isPublic: Boolean)

/**
 * 이름·직함·소개·공개할 연락처와 이력을 한 화면에서 입력하고 바로 발행하는 명함 제작 화면의 몸통.
 * 로그인 요구·발행 요청·오류 처리는 화면이 맡고, 이 위젯은 값과 변경 콜백만 받는다.
 */
@Composable fun CardComposer(
    name: String, job: String, introduction: String,
    contacts: List<CardComposerContact>, histories: List<CardComposerHistory>,
    onNameChange: (String) -> Unit, onJobChange: (String) -> Unit, onIntroductionChange: (String) -> Unit,
    onContactChange: (CardComposerContact) -> Unit, onHistoryChange: (CardComposerHistory) -> Unit,
    onPublish: () -> Unit, modifier: Modifier = Modifier,
    requiresLogin: Boolean = false, publishing: Boolean = false, errorMessage: String? = null,
    cardTitle: String? = null, onCardTitleChange: (String) -> Unit = {},
) {
    val nameMissing = name.isBlank()
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            PersonHeader(if (nameMissing) "이름" else name, if (job.isBlank()) "직함" else job, "")
            // 명함 이름은 [cardTitle]을 줄 때만 보이는 선택 칸이며, 비워 두면 화면이 '내 명함'을 쓴다.
            if (cardTitle != null) Section("명함") {
                Labeled("명함 이름 (선택)") { DearbyInlineField("명함 이름", cardTitle, onCardTitleChange, editing = true, placeholder = "비워 두면 '내 명함'으로 저장해요") }
            }
            Section("기본 정보") {
                Labeled("이름") { DearbyInlineField("이름", name, onNameChange, editing = true, placeholder = "실명 또는 활동명") }
                Labeled("직함") { DearbyInlineField("직함", job, onJobChange, editing = true, placeholder = "예: 서비스 기획 · 커뮤니티") }
                Labeled("소개") { DearbyInlineField("소개", introduction, onIntroductionChange, editing = true, placeholder = "처음 만난 사람에게 건넬 한두 문장", singleLine = false) }
            }
            if (contacts.isNotEmpty()) Section("공개할 연락처") {
                contacts.forEach { contact ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ContactSymbol(contact.kind, Modifier.size(24.dp))
                        DearbyInlineField(contact.label, contact.value, { onContactChange(contact.copy(value = it)) }, editing = true, modifier = Modifier.weight(1f), placeholder = contact.label)
                        Switch(contact.isPublic, { onContactChange(contact.copy(isPublic = it)) }, Modifier.semantics { contentDescription = "${contact.label} 공개" })
                    }
                }
            }
            if (histories.isNotEmpty()) Section("공개할 활동 이력") {
                histories.forEach { history ->
                    Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(history.title, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            if (history.detail.isNotBlank()) Text(history.detail, color = Quiet, style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(history.isPublic, { onHistoryChange(history.copy(isPublic = it)) }, Modifier.semantics { contentDescription = "${history.title} 공개" })
                    }
                }
            }
        }
        HorizontalDivider(color = Line)
        Column(Modifier.background(Color.White).padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                errorMessage != null -> Text(errorMessage, Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
                nameMissing -> Text("이름을 입력하면 발행할 수 있어요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
            }
            val label = if (requiresLogin) "로그인하고 명함 발행" else "명함 발행"
            DearbyButton(onPublish, Modifier.fillMaxWidth().semantics { if (publishing) stateDescription = "발행 중" }, enabled = !nameMissing && !publishing) {
                if (publishing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp) else Text(label, style = MaterialTheme.typography.titleMedium)
            }
            if (requiresLogin) Text("발행할 때만 이메일 인증번호로 로그인해요.", color = Quiet, style = MaterialTheme.typography.labelSmall)
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
