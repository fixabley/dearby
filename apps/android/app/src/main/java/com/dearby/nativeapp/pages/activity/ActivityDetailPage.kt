package com.dearby.nativeapp.pages.activity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/**
 * [openLink] opens the CTA link: the application link (`applicationUrl`, else the official page) until recruitment is closed, then the
 * official page as the notice (contract "신청 확인 조건 완화"). [openMap] opens the
 * curated place in a map app (contract "활동 장소"); the `장소` section shows only when the activity has one.
 */
@Composable fun ActivityDetailPage(
    activity: ActivityState, error: String?, back: () -> Unit, openLink: (String) -> Unit, editReport: () -> Unit, confirm: (Boolean) -> Unit, checkCalendar: () -> Unit, share: () -> Unit,
    openMap: () -> Unit = {}, addToMine: () -> Unit = {}, viewInMine: () -> Unit = {}, removeFromMine: () -> Unit = {},
) {
    var menu by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        DearbyTopBar("활동 상세", back) { if (activity.officialUrl != null) IconButton(share) { Icon(Icons.Rounded.Share, "활동 공유", tint = MaterialTheme.colorScheme.primary) } }
        if (activity.applied) Surface(color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.onPrimaryContainer); Text("신청했다고 표시했어요 · 실제 접수 확인이 아니에요", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                }
                ConfirmToggle(activity, confirm)
                Text(CONFIRM_NOTE, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        // No image header (contract "활동 상세 이미지 섹션 제거", 2026-10-19 밤): status, tags and title start at the top.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ExampleBadge(activity.status, true); activity.category?.let { ExampleBadge(it) } }
                // 얻을 수 있는 것 (contract "태그"): every tag, wrapping.
                if (activity.tags.isNotEmpty()) TagChips(activity.tags)
                Text(activity.title, style = MaterialTheme.typography.headlineMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.Business, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(activity.organization, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                }
                HorizontalDivider()
                InfoRow("모집 상태", activity.status, Icons.Rounded.Event)
                activity.period?.let { InfoRow("모집 기간", it, Icons.Rounded.Event) }
                InfoRow("개최 일시", activity.date.ifEmpty { "미확인" }, Icons.Rounded.Schedule)
                InfoRow("장소", activity.location ?: "미확인", Icons.Rounded.Place)
                InfoRow("프로그램", activity.program, Icons.Rounded.Badge)
                activity.place?.let { place ->
                    HorizontalDivider()
                    Text("장소", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    // A plain `ListItem` row, not a card (contract "카드는 섹션을 나눌 때만"): the whole row opens the map app.
                    ListItem({ Text(place.name) }, Modifier.clickable(role = Role.Button, onClick = openMap).semantics { contentDescription = "장소 ${place.name}, ${place.address}, 지도 앱으로 열기" },
                        supportingContent = { Text(place.address) }, leadingContent = { Icon(Icons.Rounded.Map, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent))
                }
                HorizontalDivider()
                Text("소개", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text(activity.summary)
                if (activity.eligibility.isNotEmpty()) {
                    HorizontalDivider()
                    Text("대상", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    EligibilityChips(activity.eligibility)
                }
                HorizontalDivider()
                Text("행사 일정", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text(activity.date.ifEmpty { "일정 미확인" }, style = MaterialTheme.typography.bodyMedium)
                Column { activity.sessions.forEachIndexed { index, item ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(item.first, Modifier.width(106.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        TimelineEntry("", item.second, "", last = index == activity.sessions.lastIndex, modifier = Modifier.weight(1f), compact = false)
                    }
                } }
                // The overlap check reads the device calendar on tap (contract "기기 캘린더 겹침") and needs timed schedules.
                if (activity.sessions.isNotEmpty()) {
                    DearbyOutlineButton(checkCalendar, Modifier.fillMaxWidth()) { Text("겹치는 시간 확인하기") }
                    Text("누르면 캘린더 읽기 권한을 묻고 기기 안에서만 비교해요.", Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider()
                Text("신청 조건과 최신 일정은 공식 사이트에서 확인해 주세요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("신청 표시는 이 앱 안에서만 남기는 기록이에요. 실제 접수 여부와 무관해요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                TextButton(editReport) { Text("신청 상태 수정") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (activity.applyUrl ?: activity.officialUrl)?.let { url ->
                // Closed → 공식 안내 보기; otherwise the application link, named by whether the operator set one.
                val label = when { activity.applyUrl == null -> "공식 안내 보기"; activity.applicationUrl != null -> "신청 사이트 열기"; else -> "공식 사이트에서 신청" }
                DearbyButton({ openLink(url) }, Modifier.fillMaxWidth().semantics { contentDescription = "$label, 외부 브라우저로 열려요" }) { Text(label) }
            }
            // 내 활동 직접 추가 (contract, 2026-10-20): whatever the recruitment state, the activity can be marked applied by hand — the same record as
            // the "신청했어요" answer. Once in 내 활동 the button opens it there and the menu beside it offers 내 활동에서 빼기 after a confirmation.
            if (!activity.applied) {
                DearbyOutlineButton(addToMine, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Checklist, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("내 활동에 추가") }
                Text("직접 남기는 기록이며 주최 측 확인은 아니에요", Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                DearbyOutlineButton(viewInMine, Modifier.weight(1f)) { Icon(Icons.Rounded.Checklist, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("내 활동에서 보기") }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Rounded.ExpandMore, "내 활동 메뉴") }
                    DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("내 활동에서 빼기", color = MaterialTheme.colorScheme.error) }, { menu = false; removing = true }) }
                }
            }
        }
    }
    if (removing) AlertDialog(onDismissRequest = { removing = false }, title = { Text("내 활동에서 뺄까요?") }, text = { Text("신청·참여 확정·단계 표시가 이 기기에서 지워져요.") },
        confirmButton = { TextButton({ removing = false; removeFromMine() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("빼기") } },
        dismissButton = { TextButton({ removing = false }) { Text("취소") } })
}

/** Contract "대상자 조건": each line is one condition (an `any` group already reads `… 또는 …`), shown as a soft chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable private fun TagChips(tags: List<String>) = FlowRow(Modifier.semantics { contentDescription = "태그 " + tags.joinToString(", ") }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    tags.forEach { ExampleBadge(it) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun EligibilityChips(lines: List<String>) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEach { line ->
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest)) {
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
