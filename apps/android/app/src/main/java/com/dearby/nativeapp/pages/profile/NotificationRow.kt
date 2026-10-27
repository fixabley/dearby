package com.dearby.nativeapp.pages.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 설정 `알림` 섹션의 행 (contract "설정" 2026-10-25): the `새 모집 알림` switch ([on], disabled while [saving]); when the system has this
 * app's notifications off ([systemOff]) a line says so with a link to the system notification settings ([openSettings]).
 */
@Composable fun NotificationRow(on: Boolean, saving: Boolean, systemOff: Boolean, toggle: (Boolean) -> Unit, openSettings: () -> Unit) {
    Column {
        ListItem({ Text("새 모집 알림") }, Modifier.toggleable(on, enabled = !saving, role = Role.Switch, onValueChange = toggle),
            supportingContent = { Text("즐겨찾기한 프로그램에 새 모집이 열리면 알려 드려요") }, trailingContent = { Switch(on, null, enabled = !saving) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        if (systemOff) ListItem({ Text("기기 설정에서 알림이 꺼져 있어요") }, supportingContent = { Text("켜야 알림을 받을 수 있어요") },
            trailingContent = { TextButton(openSettings) { Text("설정 열기") } }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    }
}

/**
 * 설정 `알림` `세션 이동 알림` (2026-10-26 "찜한 세션 이동 알림"): the device-local switch ([on]); when exact alarms are not allowed ([inexact], Android 14+
 * until the user allows them) a line says the reminder may come a few minutes late, with a link to that system setting ([openSettings]).
 */
@Composable fun SessionReminderRow(on: Boolean, inexact: Boolean, toggle: (Boolean) -> Unit, openSettings: () -> Unit) {
    Column {
        ListItem({ Text("세션 이동 알림") }, Modifier.toggleable(on, role = Role.Switch, onValueChange = toggle),
            supportingContent = { Text("내 시간표의 세션 10분 전에 이 기기에서 알려요") }, trailingContent = { Switch(on, null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent))
        if (on && inexact) ListItem({ Text("알람 권한이 없어 몇 분 늦을 수 있어요") }, supportingContent = { Text("정확한 시간에 받으려면 알람 및 리마인더를 허용해 주세요") },
            trailingContent = { TextButton(openSettings) { Text("설정 열기") } }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
    }
}

/** 설정 `알림` `행사 진행 중 표시` (2026-10-26 "행사 당일 진행 중 알림"): the device-local switch ([on]) for the ongoing notification on the day of a 내 활동 with 찜 sessions. */
@Composable fun LiveSessionRow(on: Boolean, toggle: (Boolean) -> Unit) {
    ListItem({ Text("행사 진행 중 표시") }, Modifier.toggleable(on, role = Role.Switch, onValueChange = toggle),
        supportingContent = { Text("행사 당일 내 시간표의 지금·다음 세션을 알림에 계속 보여요") }, trailingContent = { Switch(on, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

/**
 * Debug only (2026-10-27): 설정 › 알림 `행사 진행 중 표시 미리보기` — [on] while the made-up 행사 진행 중 notification is showing; [toggle] posts it or removes it.
 * Not in the release settings (`BuildConfig.DEBUG` at the caller).
 */
@Composable fun LiveSessionPreviewRow(on: Boolean, toggle: () -> Unit) {
    ListItem({ Text("행사 진행 중 표시 미리보기") }, Modifier.clickable(onClick = toggle, role = Role.Button),
        supportingContent = { Text(if (on) "가짜 행사 알림이 보이는 중이에요. 누르면 지워요" else "가짜 행사로 진행 중 알림을 바로 띄워요 (Debug)") },
        trailingContent = { Text(if (on) "지우기" else "띄우기", color = MaterialTheme.colorScheme.primary) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

/** 설정 `개인정보` `사용 통계 보내기` (2026-10-25): screens and a few actions, never name or contacts; on until turned off, signed in or not. */
/** 설정 › 개인정보 `광고 목적 정보 제공` (contract "광고 목적 개인정보 제3자 제공 동의(선택)"): [toggle] true opens the consent sheet first. */
@Composable fun AdConsentRow(on: Boolean, enabled: Boolean, toggle: (Boolean) -> Unit) {
    ListItem({ Text("광고 목적 정보 제공") }, Modifier.toggleable(on, enabled, Role.Switch, onValueChange = toggle),
        supportingContent = { Text("끄면 광고가 보이지 않아요. 이미 시작된 광고 기능은 앱을 완전히 닫을 때까지 남을 수 있어요.") }, trailingContent = { Switch(on, null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}

@Composable fun UsageStatsRow(on: Boolean, toggle: (Boolean) -> Unit) {
    ListItem({ Text("사용 통계 보내기") }, Modifier.toggleable(on, role = Role.Switch, onValueChange = toggle),
        supportingContent = { Text("어떤 화면과 기능을 쓰는지 모아 앱을 고치는 데 써요. 이름·연락처는 보내지 않아요.") }, trailingContent = { Switch(on, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
}
