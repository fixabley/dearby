package io.fixabley.dearby.pages.settings.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun CalendarWelcomeDialog(onEnable: () -> Unit, onLater: () -> Unit) {
    AlertDialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("겹치는 일정 확인하기") },
        text = { Text("캘린더의 바쁜 시간 정보를 가져와 활동 일정과 겹치는 시간을 확인합니다. 일정 제목·장소는 표시하지 않으며, 서버로 전송하지 않습니다.") },
        confirmButton = { TextButton(onClick = onEnable) { Text("켜기") } },
        dismissButton = { TextButton(onClick = onLater) { Text("나중에") } })
}
