package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * 폼 입력 행(contract "폼 라벨·입력 크기"·"입력 영역 터치"·"키보드의 내리기 버튼", 2026-10-20, 토스 참고): [label]을 `labelLarge` 보조색으로 입력 **위에** 쌓고(떠오르는 라벨 없음),
 * Material 3 `OutlinedTextField`는 `titleLarge`(이 앱의 재정의 값 20sp; 굵기만 보통으로) 글자·같은 크기의 placeholder·최소 높이 64다. 행 어디를 눌러도(라벨·여백 포함) 필드에
 * 포커스가 가므로 루트의 빈 곳 탭 제스처가 받을 영역이 없다. [imeAction]: 마지막 칸 `Done`은 포커스를 지워 키보드를 내리고, 중간 칸 `Next`는 아래 칸으로, 여러 줄([minLines] ≥ 2)은
 * `Default`(줄바꿈). [keyboardActions]를 넘기면 그 동작이 우선한다. 도움말·오류는 `supportingText`([error]가 있으면 빨강).
 */
@Composable fun DearbyField(
    label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String? = null, error: String? = null, hint: String? = null,
    minLines: Int = 1, imeAction: ImeAction = if (minLines > 1) ImeAction.Default else ImeAction.Done, keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions? = null, focusRequester: FocusRequester? = null, readOnly: Boolean = false, trailingIcon: (@Composable () -> Unit)? = null,
) {
    val requester = focusRequester ?: remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Normal)
    val actions = keyboardActions ?: KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }, onDone = { focusManager.clearFocus() })
    Column(modifier.fillMaxWidth().pointerInput(requester) { detectTapGestures { requester.requestFocus() } }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(value, onValueChange, Modifier.fillMaxWidth().heightIn(min = 64.dp).focusRequester(requester), textStyle = style, readOnly = readOnly,
            placeholder = placeholder?.let { { Text(it, style = style) } }, isError = error != null, supportingText = (error ?: hint)?.let { { Text(it, style = MaterialTheme.typography.bodyMedium) } },
            trailingIcon = trailingIcon, keyboardOptions = keyboardOptions.copy(imeAction = imeAction), keyboardActions = actions, singleLine = minLines == 1, minLines = minLines)
    }
}

/** 추가 행(contract "입력 영역 터치": `연락처 추가`·`이력 추가`·`사진/PDF 올리기`): 행 전체가 누를 수 있는 `ListItem`(48dp 이상), 아이콘+라벨 왼쪽, 청록 액센트. */
@Composable fun AddRow(label: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Rounded.Add, enabled: Boolean = true, onClick: () -> Unit) =
    ListItem({ Text(label, color = MaterialTheme.colorScheme.primary) }, modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
