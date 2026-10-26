package com.dearby.nativeapp.pages.activity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ViewTimeline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.TimetableMark
import com.dearby.nativeapp.widgets.activity.progress.CalendarDraft
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 활동 일정 rows grouped by day (2026-10-26 사용자 "10/28일 누르면 시간표보기 일정추가 이런식으로"): the timed blocks of one Seoul date fold into
 * one [Day] row (`10월 28일 (수) · 10:00 ~ 17:30 · 블록 4개`) that unfolds to `시간표 보기`, `캘린더에 추가` and the block rows; a date with one
 * block, and every all-day, undated, repeating or activity-period row, stays a plain [One] row where it was. Rows keep their order; a day
 * stands where its first block stood.
 */
sealed interface ScheduleGroup<T> {
    data class One<T>(val item: T) : ScheduleGroup<T>
    data class Day<T>(val day: LocalDate, val items: List<T>, val blocks: List<DayBlock>) : ScheduleGroup<T> {
        val hasTimetable get() = blocks.any { it.hasTimetable }
        /** One calendar event over the day's whole range (contract "내 캘린더에 추가"); [title] is `활동명 · 10월 28일 (수)`. */
        fun draft(title: String) = CalendarDraft(title, blocks.minOf { it.startAt.toInstant() }.toEpochMilli(), blocks.maxOf { it.endAt.toInstant() }.toEpochMilli(), false)
    }
}

/** [block] gives a row's [DayBlock] when it joins a day (a timed block that is not the activity period or repeating), else null. */
fun <T> dayGroups(items: List<T>, block: (T) -> DayBlock?): List<ScheduleGroup<T>> {
    val blocks = items.map(block)
    val days = blocks.indices.filter { blocks[it] != null }.groupBy { blocks[it]!!.startAt.toLocalDate() }.filterValues { it.size > 1 }
    val placed = mutableSetOf<LocalDate>()
    return items.indices.mapNotNull<Int, ScheduleGroup<T>> { i ->
        val day = blocks[i]?.startAt?.toLocalDate()?.takeIf { it in days } ?: return@mapNotNull ScheduleGroup.One(items[i])
        if (!placed.add(day)) null else ScheduleGroup.Day(day, days.getValue(day).map { items[it] }, days.getValue(day).map { blocks[it]!! })
    }
}

/** `10:00 ~ 17:30 · 블록 4개`: the day row's second line and the day sheet's range. */
fun daySummary(blocks: List<DayBlock>) = "${CLOCK.format(blocks.minBy { it.startAt.toInstant() }.startAt)} ~ ${CLOCK.format(blocks.maxBy { it.endAt.toInstant() }.endAt)} · 블록 ${blocks.size}개"

/** A timed block's [DayBlock] from its calendar prefill, at Seoul time. */
fun dayBlock(scheduleId: String?, title: String, draft: CalendarDraft, hasTimetable: Boolean) =
    DayBlock(scheduleId, title, seoul(draft.begin), seoul(draft.end), hasTimetable)
private fun seoul(millis: Long) = Instant.ofEpochMilli(millis).atZone(SEOUL).toOffsetDateTime()
private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.KOREAN)
private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)
/** `10월 28일 (수)`. */
fun dayLabel(day: LocalDate): String = DAY.format(day)

/**
 * One day's row: the date and [daySummary], a `시간표` mark when a block has one, an expand chevron; unfolded, `시간표 보기`
 * ([viewTimetable], hidden without a timetable) and `캘린더에 추가` ([addToCalendar]) above the block rows in [content].
 */
@Composable internal fun DayRow(day: ScheduleGroup.Day<*>, expanded: Boolean, toggle: () -> Unit, viewTimetable: () -> Unit, addToCalendar: () -> Unit, content: @Composable () -> Unit) = Column {
    ListItem({ Text(dayLabel(day.day)) }, Modifier.clickable(role = Role.Button, onClickLabel = if (expanded) "접기" else "펼치기", onClick = toggle),
        supportingContent = { Text(daySummary(day.blocks)) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        trailingContent = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (day.hasTimetable) TimetableMark()
            Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } })
    AnimatedVisibility(expanded) {
        Column(Modifier.padding(start = 16.dp)) {
            Row(Modifier.padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (day.hasTimetable) TextButton(viewTimetable, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Rounded.ViewTimeline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("시간표 보기") }
                TextButton(addToCalendar, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Rounded.EventAvailable, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("캘린더에 추가") }
            }
            content()
        }
    }
}
