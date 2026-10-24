package com.dearby.nativeapp.pages.activity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import com.dearby.nativeapp.widgets.activity.progress.CalendarDraft

/**
 * [openLink] opens the CTA link: the application link (`applicationUrl`, else the official page) until recruitment is closed, then the
 * official page as the notice (contract "신청 확인 조건 완화"). [openMap] opens the
 * curated place in a map app (contract "활동 장소"); the `장소` section shows only when the activity has one.
 */
@Composable fun ActivityDetailPage(
    activity: ActivityState, error: String?, back: () -> Unit, openLink: (String) -> Unit, editReport: () -> Unit, checkCalendar: () -> Unit, share: () -> Unit,
    openMap: () -> Unit = {}, addToMine: () -> Unit = {}, viewInMine: () -> Unit = {}, addToCalendar: (CalendarDraft) -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        // 추가 sits beside share until the activity is in 내 활동 (contract "활동 상세 내 활동 행동 정리", 2026-10-22); share is always there
        // and sends the Dearby link (contract "유니버설 링크·App Links·활동 공유·오픈그래프").
        DearbyTopBar("활동 상세", back) {
            if (!activity.applied) TextButton(addToMine, Modifier.semantics { contentDescription = "내 활동에 추가" }) { Text("추가") }
            IconButton(share) { Icon(Icons.Rounded.Share, "활동 공유", tint = MaterialTheme.colorScheme.primary) }
        }
        // No image header (contract "활동 상세 이미지 섹션 제거", 2026-10-19 밤): status and title start at the top.
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { ExampleBadge(activity.status, true); activity.category?.let { ExampleBadge(it) } }
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
                // Contract "분야·성격 태그와 아이콘": every 분야, then every 성격, as icon + text chips; an empty group is left out.
                TagGroup("분야", activity.fieldTagsAll)
                TagGroup("얻을 수 있는 것", activity.traitTags)
                if (activity.eligibility.isNotEmpty()) {
                    HorizontalDivider()
                    Text("대상", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    EligibilityChips(activity.eligibility)
                }
                HorizontalDivider()
                Text("행사 일정", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text(activity.date.ifEmpty { "일정 미확인" }, style = MaterialTheme.typography.bodyMedium)
                // 모집 마감, each schedule and the activity period as plain rows, each dated one with `캘린더에 추가` (contract "내 캘린더에 추가").
                Column { activity.schedules.forEach { item ->
                    ListItem({ Text(item.name) }, supportingContent = { Text(item.dateText) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        trailingContent = item.draft?.let { draft -> { IconButton({ addToCalendar(draft) }) { Icon(Icons.Rounded.EventAvailable, "${item.name} 캘린더에 추가", tint = MaterialTheme.colorScheme.primary) } } })
                } }
                // The overlap check reads the device calendar on tap (contract "기기 캘린더 겹침"); it needs one comparable schedule — timed,
                // or date-only with the admin's overlap-check time (contract "겹침 확인 시간", 2026-10-22). Otherwise one line says why not.
                if (activity.overlapCheckable) {
                    DearbyOutlineButton(checkCalendar, Modifier.fillMaxWidth()) { Text("겹치는 시간 확인하기") }
                    Text("누르면 캘린더 읽기 권한을 묻고 기기 안에서만 비교해요.", Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else Text("이 활동은 시간이 정해진 일정이 없어 겹침을 확인할 수 없어요", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                HorizontalDivider()
                Text("신청 조건과 최신 일정은 공식 사이트에서 확인해 주세요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Text("신청 표시는 내가 직접 남기는 기록이에요. 실제 접수 여부와 무관해요.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                TextButton(editReport) { Text("신청 상태 수정") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Once in 내 활동 the CTA block grows upward with "이미 추가된 항목입니다" and a trailing 내 활동으로 link to that activity
            // (contract "활동 상세 내 활동 행동 정리"); removing happens only by swiping in the 내 활동 list.
            if (activity.applied) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text("이미 추가된 항목입니다", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    TextButton(viewInMine) {
                        Text("내 활동으로")
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp))
                    }
                }
            }
            (activity.applyUrl ?: activity.officialUrl)?.let { url ->
                // Closed → 공식 안내 보기; otherwise the application link, named by whether the operator set one.
                val label = when { activity.applyUrl == null -> "공식 안내 보기"; activity.applicationUrl != null -> "신청 사이트 열기"; else -> "공식 사이트에서 신청" }
                DearbyButton({ openLink(url) }, Modifier.fillMaxWidth().semantics { contentDescription = "$label, 외부 브라우저로 열려요" }) { Text(label) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun TagGroup(title: String, tags: List<TagLabel>) {
    if (tags.isEmpty()) return
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { TagChip(it) } }
}

/** Contract "대상자 조건": each line is one condition (an `any` group already reads `… 또는 …`), shown as a soft chip. */
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
        Text("내가 직접 남기는 표시예요. 주최 측 접수 확인이 아니에요.")
    }, confirmButton = { TextButton({ report(true) }) { Text("신청함") } }, dismissButton = {
        Row { TextButton({ report(false) }) { Text("신청 안 함") }; TextButton(later) { Text("나중에") } }
    })
}
