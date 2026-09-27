package com.dearby.nativeapp.pages.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun ActivityDetailPage(activity: ActivityState, writing: Boolean, canSave: Boolean, error: String?, back: () -> Unit, saveProgram: () -> Unit, saveOrganization: () -> Unit, source: () -> Unit, apply: () -> Unit, editReport: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TextButton(back) { Text("목록으로") }
        if (activity.report == "applied") Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.CheckCircle, "신청 기록됨"); Text("이 활동은 이미 신청한 활동이에요.", style = MaterialTheme.typography.labelLarge) }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(activity.organization, color = MaterialTheme.colorScheme.primary)
            Text(activity.title, style = MaterialTheme.typography.headlineMedium)
            Text(activity.program, style = MaterialTheme.typography.titleSmall)
            Text(activity.status + " · " + activity.participation)
            Text(activity.summary)
            Text("일정", style = MaterialTheme.typography.titleLarge); Text(activity.date)
            activity.fields.forEach { (label, value) -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(label, style = MaterialTheme.typography.titleSmall); Text(value) } }
            HorizontalDivider()
            Text("출처", style = MaterialTheme.typography.titleLarge)
            Text("확인 시각: ${activity.checked}"); Text(activity.sourceNote)
            Text(activity.source, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(source) { Text("공식 사이트 보기") }
            OutlinedButton(saveProgram, enabled = canSave && !writing) { Text(if (activity.programSaved) "프로그램 저장 해제" else "프로그램 저장") }
            OutlinedButton(saveOrganization, enabled = canSave && !writing) { Text(if (activity.organizationSaved) "조직 저장 해제" else "조직 저장") }
            Text("저장과 신청 기록은 이 기기에만 보관됩니다. 신청 기록은 주최 측의 접수·선정·결제 확인이 아닙니다.", style = MaterialTheme.typography.bodySmall)
            Text(when (activity.report) { "applied" -> "내 기록: 신청함"; "not_applied" -> "내 기록: 신청하지 않음"; else -> "내 기록: 아직 확인하지 않음" })
            TextButton(editReport, enabled = canSave && !writing) { Text("신청 상태 수정") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("외부 사이트에서 직접 입력하고 제출해 주세요. 자동 입력과 기기 캘린더 충돌 확인은 아직 지원하지 않습니다.", style = MaterialTheme.typography.bodySmall)
            if (activity.report == "applied") Button(source) { Text("공식 사이트에서 확인") }
            else Button(apply, enabled = activity.application != null) { Text("신청 사이트 열기") }
        }
    }
}

@Composable fun ApplicationReportDialog(writing: Boolean, error: String?, report: (String) -> Unit, later: () -> Unit) {
    AlertDialog(containerColor = MaterialTheme.colorScheme.surface, onDismissRequest = later, title = { Text("신청하셨나요?") }, text = { Column {
        Text("직접 제출한 결과를 기록해 주세요. 주최 측의 참가 확정과는 다릅니다.")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    } }, confirmButton = { TextButton({ report("applied") }, enabled = !writing) { Text("신청했어요") } }, dismissButton = {
        Row { TextButton({ report("not_applied") }, enabled = !writing) { Text("신청하지 않았어요") }; TextButton(later, enabled = !writing) { Text("나중에") } }
    })
}
