package com.dearby.nativeapp.shared.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * 폼 입력 행(contract "플로팅 라벨·필수 표시"·"입력 영역 터치"·"키보드의 내리기 버튼", 2026-10-20): 칸은 검색창 같은 작은 상자(둥근 10, `surfaceContainerHighest` 채움, 높이 48,
 * 테두리 없음, 여러 줄([minLines] ≥ 2)은 그 줄 수 높이)이고 포커스·값에 따라 모양이 바뀌지 않는다. [label]은 비어 있고 포커스가 없으면 칸 안에 placeholder처럼(`bodyLarge` 보조색)
 * 보이고, 포커스가 가거나 값이 있으면 `animateFloatAsState`(동작 줄이기면 즉시)로 작아지며 칸 **위 왼쪽(바깥)**으로 올라간다(0.75배 ≈ `bodySmall`, 보조색·포커스 시 청록·오류 시 빨강).
 * 행은 처음부터 라벨 줄 20 + 간격 4 + 칸 높이를 차지해 레이아웃이 튀지 않는다. [required]면 라벨 끝에 빨간 `*`, 접근성 이름에 ", 필수"(2026-10-19 "필수예요." 도움말 규칙은 대체됨).
 * [placeholder]는 포커스가 있고 비었을 때만 칸 안에 보이는 보기(형식 안내). [prefix]는 라벨이 떠 있을 때 값 앞에 보이는 고정 글자(사용자명 칸의 `github.com/`, 보조색). [leadingIcon](연락처 행)은 칸 안 왼쪽 24 보조색. 라벨 글자는 접근성에서 지우고 필드의
 * `contentDescription`이 유일한 이름이다. 행 어디를 눌러도 필드에 포커스가 간다. [imeAction]: 마지막 칸 `Done`은 포커스를 지워 키보드를 내리고, 중간 칸 `Next`는 아래 칸으로,
 * 여러 줄은 `Default`(줄바꿈). [keyboardActions]를 넘기면 그 동작이 우선한다. 도움말·오류 문장은 칸 아래 `bodyMedium`([error]가 있으면 빨강; 칸에 테두리가 없어 라벨 색과 이 문장이 오류 표시다).
 */
@Composable fun DearbyField(
    label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String? = null, error: String? = null, hint: String? = null,
    minLines: Int = 1, imeAction: ImeAction = if (minLines > 1) ImeAction.Default else ImeAction.Done, keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions? = null, focusRequester: FocusRequester? = null, readOnly: Boolean = false, trailingIcon: (@Composable () -> Unit)? = null,
    leadingIcon: ImageVector? = null, required: Boolean = false, prefix: String? = null,
) {
    val requester = focusRequester ?: remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val colors = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    // The text field only brings its cursor line into view; while focused, keep the whole row (label, box, hint) above the keyboard and any bar under the
    // scroll area as the keyboard slides in. The inset is read in the flow, so the rows are not recomposed per frame.
    val rowInView = remember { BringIntoViewRequester() }
    val ime = WindowInsets.ime
    val density = LocalDensity.current
    LaunchedEffect(focused) { if (focused) snapshotFlow { ime.getBottom(density) }.collect { rowInView.bringIntoView() } }
    val floating = focused || value.isNotEmpty()
    val reduceMotion = rememberReduceMotion()
    val progress by animateFloatAsState(if (floating) 1f else 0f, if (reduceMotion) snap() else tween(200), label = "floatingLabel")
    val restStyle = MaterialTheme.typography.bodyLarge
    val actions = keyboardActions ?: KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }, onDone = { focusManager.clearFocus() })
    val labelColor = when { error != null -> colors.error; floating && focused -> colors.primary; else -> colors.onSurfaceVariant }
    val labelText = buildAnnotatedString { append(label); if (required) withStyle(SpanStyle(color = colors.error)) { append("*") } }
    val multiline = minLines > 1
    // Where the label rests inside the box: after the box padding (and the leading icon); it floats to the box's top-left corner, above it.
    val restX = FIELD_PADDING + if (leadingIcon == null) 0.dp else ICON_SIZE + ICON_GAP
    val restY = LABEL_LINE + LABEL_GAP + FIELD_PADDING_VERTICAL
    Column(modifier.fillMaxWidth().bringIntoViewRequester(rowInView).pointerInput(requester) { detectTapGestures { requester.requestFocus() } }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth()) {
            Column {
                Spacer(Modifier.height(LABEL_LINE + LABEL_GAP))
                FieldBox(multiline = multiline, leadingIcon = leadingIcon, trailing = trailingIcon) {
                    BasicTextField(value, onValueChange,
                        Modifier.weight(1f).padding(vertical = FIELD_PADDING_VERTICAL).focusRequester(requester).onFocusChanged { focused = it.isFocused }
                            .semantics { contentDescription = if (required) "$label, 필수" else label },
                        readOnly = readOnly, textStyle = restStyle.copy(color = colors.onSurface), keyboardOptions = keyboardOptions.copy(imeAction = imeAction), keyboardActions = actions,
                        singleLine = !multiline, minLines = minLines, cursorBrush = SolidColor(colors.primary)) { inner ->
                        Row {
                            if (prefix != null && floating) Text(prefix, Modifier.clearAndSetSemantics {}, color = colors.onSurfaceVariant, style = restStyle)
                            Box { if (value.isEmpty() && focused && placeholder != null) Text(placeholder, color = colors.onSurfaceVariant, style = restStyle); inner() }
                        }
                    }
                }
            }
            Text(labelText, Modifier.clearAndSetSemantics {}.graphicsLayer {
                translationX = restX.toPx() * (1f - progress); translationY = restY.toPx() * (1f - progress)
                scaleX = 1f - 0.25f * progress; scaleY = scaleX; transformOrigin = TransformOrigin(0f, 0f)
            }, color = labelColor, maxLines = 1, style = restStyle)
        }
        (error ?: hint)?.let { Text(it, color = if (error != null) colors.error else colors.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
    }
}

/**
 * 입력 칸·검색창이 함께 쓰는 상자(lead 2026-10-20 "검색창과 입력 칸은 같은 모양"): 둥근 10, `surfaceContainerHighest` 채움, 높이 48 이상, 테두리 없음, 안쪽 여백 16(오른쪽에 버튼이 있으면 4).
 * [leadingIcon]은 왼쪽 24 보조색, [field]는 세로 여백 12를 스스로 두고(한 줄 24 → 48), [trailing]은 오른쪽 끝.
 */
@Composable internal fun FieldBox(modifier: Modifier = Modifier, multiline: Boolean = false, leadingIcon: ImageVector? = null, trailing: (@Composable () -> Unit)? = null, field: @Composable RowScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().heightIn(min = FIELD_HEIGHT).background(colors.surfaceContainerHighest, RoundedCornerShape(10.dp))
        .padding(start = FIELD_PADDING, end = if (trailing == null) FIELD_PADDING else 4.dp), verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically) {
        if (leadingIcon != null) Icon(leadingIcon, null, Modifier.padding(top = if (multiline) FIELD_PADDING_VERTICAL else 0.dp, end = ICON_GAP).size(ICON_SIZE), tint = colors.onSurfaceVariant)
        field()
        trailing?.invoke()
    }
}

internal val FIELD_PADDING_VERTICAL = 12.dp
private val LABEL_LINE = 20.dp
private val LABEL_GAP = 4.dp
private val FIELD_HEIGHT = 48.dp
private val FIELD_PADDING = 16.dp
private val ICON_SIZE = 24.dp
private val ICON_GAP = 12.dp

/** 추가 행(contract "입력 영역 터치": `연락처 추가`·`이력 추가`·`사진/PDF 올리기`): 행 전체가 누를 수 있는 `ListItem`(48dp 이상), 아이콘+라벨 왼쪽, 청록 액센트. */
@Composable fun AddRow(label: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Rounded.Add, enabled: Boolean = true, onClick: () -> Unit) =
    ListItem({ Text(label, color = MaterialTheme.colorScheme.primary) }, modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
