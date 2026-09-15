package io.fixabley.dearby.pages.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.fixabley.dearby.pages.settings.model.SettingsState
import io.fixabley.dearby.shared.ui.theme.Spacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(state: SettingsState, onToggle: (Boolean) -> Unit, onSystemSettings: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(Spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            Text("환경설정", style = MaterialTheme.typography.headlineSmall)
            ListItem(headlineContent = { Text("겹치는 일정 확인하기") },
                supportingContent = { Text("활동 시간과 기기 캘린더의 바쁜 시간을 비교합니다.") },
                trailingContent = { Switch(state.enabled, onCheckedChange = onToggle, enabled = !state.waiting,
                    modifier = Modifier.testTag("settings.calendar.switch").semantics { contentDescription = "겹치는 일정 확인하기" }) })
            Text(state.message, style = MaterialTheme.typography.bodyMedium)
            Text("일정 제목·장소는 표시하지 않으며 서버로 전송하지 않습니다. 설정을 꺼도 기기의 캘린더 권한은 유지됩니다.",
                style = MaterialTheme.typography.bodySmall)
            if (state.showSystemSettings) TextButton(onClick = onSystemSettings) { Text("앱 권한 설정") }
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    }
}
