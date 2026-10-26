package com.dearby.nativeapp.pages.activity

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import java.time.format.TextStyle
import java.util.Locale

/**
 * 즐겨찾기 › 내 시간표 (2026-10-26 사용자 "찜한 시간표만 따로"): `GET /v1/me/session-favorites` as 나만의 시간표, grouped by activity (its
 * name opens the activity) and then by the session's own date. A heart tap un-찜s at once; a tap on a card opens its description.
 */
@Composable fun MySessionsPage(entries: List<MyTimetableEntry>, favorites: Set<String>, heart: (String) -> Unit, openActivity: (String) -> Unit, back: () -> Unit) {
    var opened by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        DearbyTopBar("내 시간표", back)
        if (entries.isEmpty()) DearbyEmptyState("내 시간표가 비어 있어요", "세션에 하트를 누르면 내 시간표에 모여요.", Modifier.fillMaxSize(), Icons.Rounded.FavoriteBorder)
        else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            entries.groupBy { it.activityId }.forEach { (activityId, ofActivity) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 8.dp).clickable(role = Role.Button) { openActivity(activityId) }.semantics { heading() },
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(ofActivity.first().activityName, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                ofActivity.groupBy { it.session.startAt.toLocalDate() }.forEach { (day, ofDay) ->
                    Text("${day.monthValue}월 ${day.dayOfMonth}일 (${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN)})", Modifier.semantics { heading() },
                        color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    MyTimetable(ofDay, favorites, heart, { opened = it.id })
                }
            }
        }
    }
    entries.firstOrNull { it.session.id == opened }?.let { entry ->
        SessionSheet(entry.session, entry.track, entry.session.id in favorites, { heart(entry.session.id) }) { opened = null }
    }
}
