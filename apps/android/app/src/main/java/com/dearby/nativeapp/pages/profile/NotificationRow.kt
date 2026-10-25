package com.dearby.nativeapp.pages.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 프로필 `알림` 묶음 (contract "즐겨찾기(하트)·새 모집 푸시 알림"): the `새 모집 알림` switch ([on], disabled while [saving]); when the system has this
 * app's notifications off ([systemOff]) a line says so with a link to the system notification settings ([openSettings]).
 */
@Composable fun NotificationRow(on: Boolean, saving: Boolean, systemOff: Boolean, toggle: (Boolean) -> Unit, openSettings: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("알림", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        ListItem({ Text("새 모집 알림") }, Modifier.toggleable(on, enabled = !saving, role = Role.Switch, onValueChange = toggle),
            supportingContent = { Text("즐겨찾기한 프로그램에 새 모집이 열리면 알려 드려요") }, trailingContent = { Switch(on, null, enabled = !saving) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
        if (systemOff) ListItem({ Text("기기 설정에서 알림이 꺼져 있어요") }, supportingContent = { Text("켜야 알림을 받을 수 있어요") },
            trailingContent = { TextButton(openSettings) { Text("설정 열기") } }, colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
    }
}

/** `사용 통계 보내기` (2026-10-25): anonymous usage statistics (screens and a few actions, no personal data), on until turned off; signed in or not. */
@Composable fun UsageStatsRow(on: Boolean, toggle: (Boolean) -> Unit) {
    ListItem({ Text("사용 통계 보내기") }, Modifier.toggleable(on, role = Role.Switch, onValueChange = toggle),
        supportingContent = { Text("어떤 화면과 기능을 쓰는지 개인정보 없이 모아 앱을 고치는 데 써요") }, trailingContent = { Switch(on, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
}
