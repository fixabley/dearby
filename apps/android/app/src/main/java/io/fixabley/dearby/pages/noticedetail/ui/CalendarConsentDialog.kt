package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
internal fun CalendarConsentDialog(onContinue: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, title = { Text("내 일정 연결") },
        text = { Text("캘린더의 바쁜 시간 정보를 가져와 활동 일정과 겹치는 시간을 확인합니다. 일정 제목·장소는 표시하지 않으며, 서버로 전송하지 않습니다.") },
        confirmButton = { TextButton(onClick = onContinue) { Text("계속") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("취소") } })
}
