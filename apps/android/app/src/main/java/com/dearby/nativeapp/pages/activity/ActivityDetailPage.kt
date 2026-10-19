package com.dearby.nativeapp.pages.activity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/** [openLink] opens the official page: as the application link while recruiting, otherwise as the notice. */
@Composable fun ActivityDetailPage(activity: ActivityState, error: String?, back: () -> Unit, openLink: (String) -> Unit, editReport: () -> Unit, confirm: (Boolean) -> Unit, checkCalendar: () -> Unit, share: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("활동 상세", back) { if (activity.officialUrl != null) IconButton(share) { Icon(Icons.Outlined.IosShare, "활동 공유", tint = Teal) } }
        if (activity.applied) Surface(color = Mint) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = Teal); Text("신청했다고 표시했어요 · 실제 접수 확인이 아니에요", color = Teal, style = MaterialTheme.typography.bodyMedium)
                }
                ConfirmToggle(activity, confirm)
                Text(CONFIRM_NOTE, color = Quiet, style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ActivityImage(activity.imageUrl, Modifier.fillMaxWidth().height(205.dp))
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ExampleBadge(activity.status, true); activity.category?.let { ExampleBadge(it) } }
                Text(activity.title, style = MaterialTheme.typography.headlineMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Business, null, Modifier.size(38.dp), tint = Teal)
                    Text(activity.organization, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
                InfoRow("모집 상태", activity.status, Icons.Outlined.CalendarToday)
                activity.period?.let { InfoRow("모집 기간", it, Icons.Outlined.EventAvailable) }
                InfoRow("개최 일시", activity.date.ifEmpty { "미확인" }, Icons.Outlined.Schedule)
                InfoRow("장소", activity.location ?: "미확인", Icons.Outlined.LocationOn)
                InfoRow("프로그램", activity.program, Icons.Outlined.Badge)
                HorizontalDivider()
                Text("소개", style = MaterialTheme.typography.titleLarge, color = Teal)
                Text(activity.summary)
                if (activity.eligibility.isNotEmpty()) {
                    HorizontalDivider()
                    Text("대상", style = MaterialTheme.typography.titleLarge, color = Teal)
                    EligibilityChips(activity.eligibility)
                }
                HorizontalDivider()
                Text("행사 일정", style = MaterialTheme.typography.titleLarge, color = Teal)
                Text(activity.date.ifEmpty { "일정 미확인" }, style = MaterialTheme.typography.bodyMedium)
                Column { activity.sessions.forEachIndexed { index, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(item.first, Modifier.width(106.dp), color = Quiet, style = MaterialTheme.typography.bodySmall)
                        TimelineEntry("", item.second, "", last = index == activity.sessions.lastIndex, modifier = Modifier.weight(1f), compact = false)
                    }
                } }
                // The overlap check reads the device calendar on tap (contract "기기 캘린더 겹침") and needs timed schedules.
                if (activity.sessions.isNotEmpty()) {
                    DearbyOutlineButton(checkCalendar, Modifier.fillMaxWidth()) { Text("겹치는 시간 확인하기", fontWeight = FontWeight.SemiBold) }
                    Text("누르면 캘린더 읽기 권한을 묻고 기기 안에서만 비교해요.", Modifier.align(Alignment.CenterHorizontally), color = Quiet, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
                Text("신청 조건과 최신 일정은 공식 사이트에서 확인해 주세요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
                Text("신청 표시는 이 앱 안에서만 남기는 기록이에요. 실제 접수 여부와 무관해요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
                TextButton(editReport) { Text("신청 상태 수정") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        (activity.applyUrl ?: activity.officialUrl)?.let { url ->
            HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                DearbyButton({ openLink(url) }, Modifier.weight(1f).semantics { contentDescription = (if (activity.applyUrl != null) "공식 사이트에서 신청" else "공식 안내 보기") + ", 외부 브라우저로 열려요" }) {
                    Text(if (activity.applyUrl != null) "공식 사이트에서 신청" else "공식 안내 보기")
                }
            }
        }
    }
}

/** Contract "대상자 조건": each line is one condition (an `any` group already reads `… 또는 …`), shown as a soft chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun EligibilityChips(lines: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            Surface(color = Soft, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Line)) {
                Text(line, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable fun ApplicationReportDialog(report: (Boolean) -> Unit, later: () -> Unit) {
    AlertDialog(containerColor = MaterialTheme.colorScheme.surface, onDismissRequest = later, title = { Text("신청 상태") }, text = {
        Text("이 앱 안에서만 남기는 표시예요. 주최 측 접수 확인이 아니며 앱을 종료하면 사라져요.")
    }, confirmButton = { TextButton({ report(true) }) { Text("신청함") } }, dismissButton = {
        Row { TextButton({ report(false) }) { Text("신청 안 함") }; TextButton(later) { Text("나중에") } }
    })
}
