package com.dearby.nativeapp.pages.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

@Composable fun ActivityDetailPage(activity: ActivityState, error: String?, back: () -> Unit, source: () -> Unit, apply: () -> Unit, editReport: () -> Unit, confirm: (Boolean) -> Unit, checkCalendar: () -> Unit, share: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("활동 상세", back) { IconButton(share) { Icon(Icons.Outlined.IosShare, "활동 공유", tint = Teal) } }
        if (activity.applied) Surface(color = Mint) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.CheckCircle, null, tint = Teal); Text("예시 신청 상태를 기록했어요.", color = Teal, style = MaterialTheme.typography.bodyMedium)
                }
                ConfirmToggle(activity, confirm)
                Text(CONFIRM_NOTE, color = Quiet, style = MaterialTheme.typography.bodySmall)
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Image(painterResource(activityArtwork(activity.id)), null, Modifier.fillMaxWidth().height(205.dp), contentScale = ContentScale.Crop)
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ExampleBadge("예시 활동", true); ExampleBadge(activity.participation.substringBefore(" ·"), true) }
                Text(activity.title, style = MaterialTheme.typography.headlineMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.AllInclusive, null, Modifier.size(38.dp), tint = Teal)
                    Text("Dearby 커뮤니티", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
                InfoRow("참가 대상", activity.fields.first { it.first == "참가 대상" }.second, Icons.Outlined.PeopleOutline)
                InfoRow("참가비", "무료", Icons.Outlined.Toll)
                InfoRow("모집 상태", activity.status, Icons.Outlined.CalendarToday)
                InfoRow("개최 일시", activity.date, Icons.Outlined.Schedule)
                InfoRow("장소", activity.fields.first { it.first == "장소" }.second, Icons.Outlined.LocationOn)
                HorizontalDivider()
                Text("소개", style = MaterialTheme.typography.titleLarge, color = Teal)
                Text(activity.summary)
                Text("개발 · 디자인 · 기획의 경험과 협업 사례를 나누어요.\n함께 배우고 새로운 인연을 만나는 시간입니다.", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                Text("행사 일정", style = MaterialTheme.typography.titleLarge, color = Teal)
                Text(activity.date, style = MaterialTheme.typography.bodyMedium)
                Column { activity.sessions.forEachIndexed { index, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(item.first, Modifier.width(106.dp), color = Quiet, style = MaterialTheme.typography.bodySmall)
                        TimelineEntry("", item.second, "", last = index == activity.sessions.lastIndex, modifier = Modifier.weight(1f), compact = false)
                    }
                } }
                DearbyOutlineButton(checkCalendar, Modifier.fillMaxWidth()) { Text("겹치는 시간 확인하기", fontWeight = FontWeight.SemiBold) }
                Text("예시 캘린더의 바쁜 시간과 비교해요.", Modifier.align(Alignment.CenterHorizontally), color = Quiet, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("참가 안내", style = MaterialTheme.typography.titleLarge, color = Teal)
                InfoRow("참가비", "무료"); InfoRow("등록 방법", "신청 화면에서 흐름 둘러보기"); InfoRow("준비물", "없음"); InfoRow("세부 일정", "화면 확인용 고정 예시입니다.")
                HorizontalDivider()
                Text("장소", style = MaterialTheme.typography.titleLarge, color = Teal)
                Text(activity.fields.first { it.first == "장소" }.second)
                Text("가상 활동 · 디자인 예시", color = Quiet, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("정보 출처", style = MaterialTheme.typography.titleLarge, color = Teal)
                TextButton(source, contentPadding = PaddingValues(0.dp)) { Text("예시 링크 확인 ↗") }
                Text("실제 접수·선정·결제가 아닙니다. 상태는 앱 실행 중에만 유지돼요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
                TextButton(editReport) { Text("신청 상태 수정") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        HorizontalDivider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            DearbyButton(apply, Modifier.weight(1f)) { Text(if (activity.applied) "신청 화면 보기" else "공식 사이트에서 신청") }
        }
    }
}

@Composable fun ApplicationReportDialog(report: (Boolean) -> Unit, later: () -> Unit) {
    AlertDialog(containerColor = MaterialTheme.colorScheme.surface, onDismissRequest = later, title = { Text("신청 상태") }, text = {
        Text("화면 확인용 상태입니다. 실제 접수가 아니며 앱을 종료하면 초기화됩니다.")
    }, confirmButton = { TextButton({ report(true) }) { Text("신청함") } }, dismissButton = {
        Row { TextButton({ report(false) }) { Text("신청 안 함") }; TextButton(later) { Text("나중에") } }
    })
}
