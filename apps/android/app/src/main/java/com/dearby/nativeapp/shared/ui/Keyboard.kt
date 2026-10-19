package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * 입력 화면 루트에 둔다(contract "키보드 내리기", 2026-10-19 밤): 필드 밖 빈 곳을 누르면 포커스를 지워 키보드가 내려간다. 필드·버튼은 자기 탭을 먼저
 * 받아 소비하므로 영향이 없고, 스크롤은 탭이 아니라 그대로 지나간다. 스크롤 컨테이너에는 `imeNestedScroll()`, 루트에는 `imePadding()`을 함께 둔다.
 */
@Composable fun Modifier.dismissKeyboardOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    return pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
}
