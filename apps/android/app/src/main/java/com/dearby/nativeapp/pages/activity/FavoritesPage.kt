package com.dearby.nativeapp.pages.activity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.activity.programCard.ProgramCard
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 즐겨찾기 (contract "즐겨찾기(하트)·새 모집 푸시 알림" · "즐겨찾기 활동 캘린더 보기"): the `목록 | 캘린더` switch at the top right, then either the
 * 프로그램 cards (expanding in place like discovery; [openProgram] marks the `새 모집` seen and loads its activities) and the 활동 rows, or the month
 * grid over the dated list. Rows swipe away ([remove], `되돌리기` snackbar → [undo], otherwise [commit]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FavoritesPage(
    state: FavoritesViewState, list: ActivityListState, hearts: FavoriteHearts, open: (String) -> Unit, plus: (String) -> Unit, openProgram: (String) -> Unit,
    retryProgram: (String) -> Unit, remove: (Boolean, String) -> Unit, undo: () -> Unit, commit: () -> Unit, setView: (FavoritesView) -> Unit,
    signIn: () -> Unit, retry: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.undo) {
        val pending = state.undo ?: return@LaunchedEffect
        if (snackbar.showSnackbar(pending.message, "되돌리기", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) undo() else commit()
    }
    Box(Modifier.fillMaxSize()) {
        when (state.phase) {
            FavoritesPhase.SIGNED_OUT -> DearbyEmptyState("로그인하면 즐겨찾기를 쓸 수 있어요", "하트를 누른 활동과 프로그램이 여기에 모여요.", Modifier.fillMaxSize(), Icons.Rounded.FavoriteBorder, MessageAction("로그인", signIn))
            FavoritesPhase.LOADING -> DearbySkeleton(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) { repeat(4) { SkeletonBlock(Modifier.fillMaxWidth().height(88.dp), RoundedCornerShape(12.dp)) } }
            FavoritesPhase.FAILED -> DearbyErrorState("즐겨찾기를 불러오지 못했어요", "연결을 확인한 뒤 다시 시도해 주세요.", retry, Modifier.fillMaxSize())
            FavoritesPhase.LOADED -> Column(Modifier.fillMaxSize()) {
                SingleChoiceSegmentedButtonRow(Modifier.align(Alignment.End).padding(horizontal = 20.dp, vertical = 8.dp)) {
                    FavoritesView.entries.forEach { view ->
                        SegmentedButton(state.view == view, { setView(view) }, SegmentedButtonDefaults.itemShape(view.ordinal, FavoritesView.entries.size)) { Text(view.label) }
                    }
                }
                val activities = state.activityIds.mapNotNull(list.known::get)
                when {
                    state.programs.isEmpty() && activities.isEmpty() -> DearbyEmptyState("하트를 누르면 여기에 모여요", "발견에서 마음에 드는 활동과 프로그램에 하트를 눌러 보세요.", Modifier.fillMaxSize(), Icons.Rounded.FavoriteBorder)
                    state.view == FavoritesView.LIST -> FavoritesList(state, activities, list, hearts, open, plus, openProgram, retryProgram, remove)
                    else -> FavoritesCalendar(state, open)
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable private fun FavoritesList(
    state: FavoritesViewState, activities: List<ActivityState>, list: ActivityListState, hearts: FavoriteHearts, open: (String) -> Unit, plus: (String) -> Unit,
    openProgram: (String) -> Unit, retryProgram: (String) -> Unit, remove: (Boolean, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(emptySet<String>()) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.programs.isNotEmpty()) item(key = "programs") { SectionHeading("프로그램") }
        items(state.programs, key = { "program-" + it.program.id }) { row ->
            val program = row.program
            Removable({ remove(true, program.id) }) {
                ProgramCard(program.title, program.organization, program.openCount, program.upcomingCount, program.deadline, program.nearestStart, program.latestActivityAt,
                    program.fieldTags, program.traitTags, program.id in expanded, {
                        if (program.id in expanded) expanded = expanded - program.id else { expanded = expanded + program.id; openProgram(program.id) }
                    }, logoUrl = program.logoUrl, newRecruitment = row.newRecruitment, actions = { FavoriteActions(hearts, true, program.id, program.title) }) {
                    ProgramBody(program.id, list, open, retryProgram, hearts, plus)
                }
            }
        }
        if (activities.isNotEmpty()) item(key = "activities") { SectionHeading("활동") }
        items(activities, key = { "activity-" + it.id }) { activity ->
            Removable({ remove(false, activity.id) }) {
                ActivityRow(activity, open, trailing = { FavoriteActions(hearts, false, activity.id, activity.title, activity.applied) { plus(activity.id) } }) {
                    Text(activity.status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable private fun SectionHeading(text: String) =
    Text(text, Modifier.padding(top = 6.dp).semantics { heading() }, style = MaterialTheme.typography.titleMedium)

/** Swipe from end to start takes the favorite away at once; the page's snackbar offers 되돌리기. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun Removable(remove: () -> Unit, content: @Composable () -> Unit) {
    val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) { remove(); true } else false })
    SwipeToDismissBox(swipe, enableDismissFromStartToEnd = false, backgroundContent = {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp)).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
            Icon(Icons.Rounded.Delete, "즐겨찾기 해제", tint = MaterialTheme.colorScheme.onErrorContainer)
        }
    }) { content() }
}

private val months = 240
private val weekdays = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
private fun YearMonth.title() = "${year}년 ${monthValue}월"

/**
 * The calendar view: a 7-column month grid (Material 3 colours and type; no stock month component fits) that pages by month, a teal dot under a
 * recruitment end, a mint ring under a recruitment start, a mint dot under an event start; below, the dated list grouped by month with
 * `날짜 미정` last. A date tap scrolls the list to it; paging scrolls to that month's group. Past dates are dimmed. Nothing is written to the device calendar.
 */
@Composable private fun FavoritesCalendar(state: FavoritesViewState, open: (String) -> Unit) {
    val today = state.today
    val start = YearMonth.from(today).minusMonths((months / 2).toLong())
    val pager = rememberPagerState(initialPage = months / 2) { months }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val dated = state.entries.filter { it.date != null }
    val byDay = dated.groupBy { it.date!! }
    val days = byDay.keys.sorted()
    // The list: a heading per month, a row per date, then 날짜 미정; the maps give each month heading's and each date's item index.
    val monthsShown = days.map { YearMonth.from(it) }.distinct()
    val monthIndex = mutableMapOf<YearMonth, Int>()
    val dayIndex = mutableMapOf<LocalDate, Int>()
    var next = 0
    monthsShown.forEach { month -> monthIndex[month] = next++; days.filter { YearMonth.from(it) == month }.forEach { dayIndex[it] = next++ } }
    val shownMonth = start.plusMonths(pager.currentPage.toLong())
    LaunchedEffect(pager.currentPage) {
        // Paging lands the list on that month's group, or the next month that has one.
        val target = monthsShown.firstOrNull { it >= shownMonth } ?: return@LaunchedEffect
        monthIndex[target]?.let { listState.animateScrollToItem(it) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "이전 달") }
            Text(shownMonth.title(), Modifier.weight(1f).semantics { heading() }, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            IconButton({ scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, enabled = pager.currentPage < months - 1) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "다음 달") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            weekdays.forEach { Text(it.getDisplayName(TextStyle.NARROW, Locale.KOREAN), Modifier.weight(1f), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) }
        }
        HorizontalPager(pager, Modifier.fillMaxWidth()) { page ->
            MonthGrid(start.plusMonths(page.toLong()), today, byDay) { day ->
                // A date with nothing scrolls to the next date that has something.
                val target = days.firstOrNull { it >= day } ?: days.lastOrNull() ?: return@MonthGrid
                dayIndex[target]?.let { scope.launch { listState.animateScrollToItem(it) } }
            }
        }
        HorizontalDivider()
        val undated = state.entries.filter { it.date == null }
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), listState, contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)) {
            monthsShown.forEach { month ->
                item(key = "month-$month") { Text(month.title(), Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall) }
                days.filter { YearMonth.from(it) == month }.forEach { day -> item(key = "day-$day") { DayRow(day, byDay.getValue(day), today, open) } }
            }
            if (undated.isNotEmpty()) {
                item(key = "undated") { Text("날짜 미정", Modifier.padding(top = 12.dp, bottom = 4.dp).semantics { heading() }, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall) }
                items(undated, key = { "undated-" + it.activityId }) { EntryLine(it, today, open, Modifier.padding(start = 64.dp)) }
            }
        }
    }
}

@Composable private fun MonthGrid(month: YearMonth, today: LocalDate, byDay: Map<LocalDate, List<CalendarEntry>>, pick: (LocalDate) -> Unit) {
    val lead = month.atDay(1).dayOfWeek.value % 7
    val cells = lead + month.lengthOfMonth()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        repeat((cells + 6) / 7) { week ->
            Row(Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val number = week * 7 + column - lead + 1
                    if (number < 1 || number > month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(48.dp))
                    else DayCell(month.atDay(number), today, byDay[month.atDay(number)].orEmpty(), pick, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable private fun DayCell(day: LocalDate, today: LocalDate, entries: List<CalendarEntry>, pick: (LocalDate) -> Unit, modifier: Modifier) {
    val marks = entries.mapNotNull { it.mark }.toSet()
    val name = "${day.monthValue}월 ${day.dayOfMonth}일" + if (entries.isEmpty()) "" else ", 일정 ${entries.size}개"
    Column(modifier.height(48.dp).clickable(role = Role.Button) { pick(day) }.semantics { contentDescription = name }.alpha(if (day < today) .38f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        val isToday = day == today
        Box(Modifier.size(30.dp).then(if (isToday) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
            Text(day.dayOfMonth.toString(), color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
        }
        Row(Modifier.height(8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (CalendarMark.DEADLINE in marks) Box(Modifier.size(6.dp).background(TealFill, CircleShape))
            if (CalendarMark.START in marks) Box(Modifier.size(6.dp).border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = .6f), CircleShape))
            if (CalendarMark.EVENT in marks) Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape).border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .4f)), CircleShape))
        }
    }
}

/** One date of the list: the big day number and weekday on the left, its entries on the right. */
@Composable private fun DayRow(day: LocalDate, entries: List<CalendarEntry>, today: LocalDate, open: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp).alpha(if (day < today) .5f else 1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.width(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(day.dayOfMonth.toString(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium, color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { entries.forEach { EntryLine(it, today, open) } }
    }
}

@Composable private fun EntryLine(entry: CalendarEntry, today: LocalDate, open: (String) -> Unit, modifier: Modifier = Modifier) {
    // A deadline within three days is red (contract "즐겨찾기 활동 캘린더 보기").
    val soon = entry.mark == CalendarMark.DEADLINE && entry.date != null && ChronoUnit.DAYS.between(today, entry.date) in 0..3
    Surface({ open(entry.activityId) }, modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text(entry.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(entry.program, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            entry.mark?.let { mark ->
                Surface(color = if (soon) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(8.dp)) {
                    Text(mark.label, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = if (soon) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
