package com.dearby.nativeapp.features.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 기기 캘린더와 활동 일정이 겹치는 한 쌍. 일정 제목·시각은 화면 표시에만 쓰고 저장하지 않는다. */
data class CalendarOverlapItem(
    val id: String, val sessionTitle: String, val sessionStart: Instant, val sessionEnd: Instant,
    val eventTitle: String, val eventStart: Instant, val eventEnd: Instant,
)

/** 겹침 확인 결과의 상태. 계산·권한 요청은 화면이 맡고 이 값만 넘긴다. */
sealed interface CalendarOverlapDisplay {
    data object Checking : CalendarOverlapDisplay
    data object Denied : CalendarOverlapDisplay
    data object Clear : CalendarOverlapDisplay
    data class Overlaps(val items: List<CalendarOverlapItem>) : CalendarOverlapDisplay
}

// 주황 계열 겹침 표시. 글자 #9D5109 / 배경 #FFF1E0 대비 약 5.5:1.
private val OverlapText = Color(0xFF9D5109)
private val OverlapFill = Color(0xFFFFF1E0)

/** 기기 캘린더 겹침 확인 결과. 확인 중·권한 거부(설정 열기)·겹침 없음·겹치는 일정 목록을 보인다. 시각은 [zone]으로 보인다. */
@Composable fun CalendarOverlapResult(display: CalendarOverlapDisplay, close: () -> Unit, modifier: Modifier = Modifier, zone: ZoneId = ZoneId.systemDefault(), openSettings: () -> Unit = {}) {
    val day = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN).withZone(zone)
    val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    fun range(start: Instant, end: Instant) = "${time.format(start)}–${time.format(end)}"
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("겹치는 시간 확인", Modifier.weight(1f).semantics { heading() }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            IconButton(close) { Icon(Icons.Outlined.Close, "닫기", tint = Quiet) }
        }
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (display) {
                CalendarOverlapDisplay.Checking -> Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Teal); Text("캘린더와 비교하는 중이에요", color = Quiet, style = MaterialTheme.typography.bodyMedium)
                }
                CalendarOverlapDisplay.Denied -> StateBlock(Icons.Outlined.EventBusy, "캘린더 접근이 꺼져 있어요", "겹치는 일정을 확인하려면 설정에서 캘린더 접근을 허용해 주세요. 허용하지 않아도 신청은 그대로 할 수 있어요.") {
                    DearbyOutlineButton(openSettings, Modifier.fillMaxWidth()) { Text("설정 열기", style = MaterialTheme.typography.titleMedium) }
                }
                CalendarOverlapDisplay.Clear -> StateBlock(Icons.Outlined.EventAvailable, "겹치는 일정이 없어요", "활동 시간에 캘린더 일정이 없어요.") {}
                is CalendarOverlapDisplay.Overlaps -> {
                    Text("${display.items.size}개 일정이 겹쳐요", Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    display.items.forEach { item ->
                        Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Line), modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(day.format(item.sessionStart), color = Quiet, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                                    Text("겹침", Modifier.background(OverlapFill, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp), color = OverlapText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                }
                                Text(item.eventTitle, style = MaterialTheme.typography.titleMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.CalendarToday, null, Modifier.size(16.dp), tint = Quiet); Text(range(item.eventStart, item.eventEnd), style = MaterialTheme.typography.bodyMedium)
                                }
                                Text("활동 일정 · ${item.sessionTitle} ${range(item.sessionStart, item.sessionEnd)}", color = Quiet, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = Quiet)
            Text("캘린더 일정은 이 기기에서만 비교하고 저장하거나 보내지 않아요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun StateBlock(icon: ImageVector, title: String, message: String, action: @Composable () -> Unit) =
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(68.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(32.dp), tint = Teal) }
        Text(title, Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
        Text(message, color = Quiet, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        action()
    }
