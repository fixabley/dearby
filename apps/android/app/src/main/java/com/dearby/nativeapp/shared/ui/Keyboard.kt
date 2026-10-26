package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager

/**
 * 입력 화면 루트에 둔다(contract "키보드 내리기", 2026-10-19 밤): 필드 밖 빈 곳을 누르면 포커스를 지워 키보드가 내려간다. 필드·버튼은 자기 탭을 먼저
 * 받아 소비하므로 영향이 없고, 스크롤은 탭이 아니라 그대로 지나간다. 스크롤 컨테이너에는 [dismissKeyboardOnScroll]을 함께 둔다. IME 인셋(`imePadding`)은 `DearbyApp` 셸이 한 번만 적용한다.
 */
@Composable fun Modifier.dismissKeyboardOnTap(): Modifier {
    val focusManager = LocalFocusManager.current
    return pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
}

/**
 * 스크롤 컨테이너에 둔다(contract "키보드 내리기": 스크롤로도 내려간다): 손가락이 터치 슬롭 넘게 끌리면 포커스를 지워 키보드가 내려간다. 이벤트는 소비하지 않아 스크롤은 그대로다.
 * 스크롤 이벤트가 아니라 손가락을 본다 — 필드가 포커스를 얻을 때의 bring-into-view 스크롤도 nested scroll `UserInput`으로 와서 막 뜬 키보드를 내린다.
 * `imeNestedScroll()`은 쓰지 않는다 — 목록 끝에서 더 끌어올리면 포커스가 없어도 키보드를 끌어올려 탭 바가 사라졌다(2026-10-26 사용자 보고).
 */
@Composable fun Modifier.dismissKeyboardOnScroll(): Modifier {
    val focusManager = LocalFocusManager.current
    return pointerInput(focusManager) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                // A long press first is text selection or a drag handle, not a scroll.
                if (!change.pressed || change.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) break
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) { focusManager.clearFocus(); break }
            }
        }
    }
}
