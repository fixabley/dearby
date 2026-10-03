package com.dearby.nativeapp.pages.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Image
import com.dearby.nativeapp.shared.ui.*
import androidx.compose.ui.unit.dp

@Composable fun ActivityDetailPage(activity: ActivityState, error: String?, back: () -> Unit, source: () -> Unit, apply: () -> Unit, editReport: () -> Unit, checkCalendar: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "목록으로") }; Text("활동 상세", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, color = Teal) }
        if (activity.report) Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.CheckCircle, "예시 신청 기록됨"); Text("예시 신청 상태를 기록했어요.", style = MaterialTheme.typography.labelLarge) }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Surface(color = Soft, modifier = Modifier.fillMaxWidth().height(168.dp)) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Outlined.Image, null, tint = Quiet); Text("공식 이미지 미제공", color = Quiet, style = MaterialTheme.typography.bodySmall) } }
            Text(activity.status + " · " + activity.participation, color = Teal, style = MaterialTheme.typography.labelLarge)
            Text(activity.title, style = MaterialTheme.typography.headlineMedium)
            Text("예시 활동", style = MaterialTheme.typography.titleSmall)
            Text("Dearby", style = MaterialTheme.typography.titleMedium)
            HorizontalDivider()
            Text("소개", style = MaterialTheme.typography.titleLarge, color = Teal)
            Text(activity.summary)
            Text("일정", style = MaterialTheme.typography.titleLarge); Text(activity.date)
            activity.fields.forEach { (label, value) -> Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { Text(label, Modifier.weight(0.35f), color = Quiet, style = MaterialTheme.typography.bodyMedium); Text(value, Modifier.weight(0.65f), style = MaterialTheme.typography.bodyMedium) } }
            DearbyOutlineButton(checkCalendar) { Text("겹치는 시간 확인하기") }
            HorizontalDivider()
            Text("출처", style = MaterialTheme.typography.titleLarge)
            Text("프로토타입용 고정 예시입니다.")
            Text(activity.source, style = MaterialTheme.typography.bodySmall)
            DearbyOutlineButton(source) { Text("예시 링크 보기") }
            Text("예시 신청 상태는 앱 실행 중에만 유지됩니다. 실제 접수·선정·결제가 아닙니다.", style = MaterialTheme.typography.bodySmall)
            Text(if (activity.report) "예시 기록: 신청함" else "예시 기록: 신청하지 않음")
            TextButton(editReport) { Text("예시 신청 상태 수정") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("화면 흐름을 살펴보는 데모이며 실제 신청을 받지 않습니다.", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DearbyButton(apply, Modifier.weight(1f)) { Text("신청 사이트 열기") }
        }
    }
}

@Composable fun ApplicationReportDialog(report: (Boolean) -> Unit, later: () -> Unit) {
    AlertDialog(containerColor = MaterialTheme.colorScheme.surface, onDismissRequest = later, title = { Text("예시 신청 상태") }, text = {
        Text("화면 확인용 상태입니다. 실제 접수가 아니며 앱을 종료하면 초기화됩니다.")
    }, confirmButton = { TextButton({ report(true) }) { Text("예시 신청함") } }, dismissButton = {
        Row { TextButton({ report(false) }) { Text("예시 신청 안 함") }; TextButton(later) { Text("나중에") } }
    })
}
