package com.dearby.nativeapp.pages.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.activity.sessionCard.SessionBand
import com.dearby.nativeapp.widgets.activity.sessionCard.SessionCard
import com.dearby.nativeapp.widgets.activity.sessionCard.TrackBadge
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * 트랙·세션 시간표 as a modal (2026-10-26 사용자 "그럼 차라리 모달로 보여줘"): a full-height Material 3 `ModalBottomSheet` opened from the
 * block row, read when it opens. On top [title] (`{활동 이름} · {블록 제목}`), [range] and 닫기, then `전체 | 찜한 세션` and the table — rows
 * are time slots ([timetableRows]), columns are tracks in `position` order. The whole table scrolls sideways at once while the time column
 * stays put; the sheet only drags vertically, so the sideways scroll and the sheet's vertical nested scroll do not compete. A block without
 * tracks is one column. A tap on a session opens its description and speakers; [heart] is the 찜 tap (the caller opens sign-in or the
 * consent screen when the account is not ready).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun TimetableSheet(title: String, range: String?, phase: TimetablePhase, timetable: TimetableState?, favorites: Set<String>, heart: (String) -> Unit, dismiss: () -> Unit, retry: () -> Unit) {
    var favoritesOnly by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<String?>(null) }
    val trackOf: (SessionState) -> TrackState? = { session -> timetable?.tracks?.firstOrNull { it.id == session.trackId } }
    ModalBottomSheet(dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    range?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                }
                IconButton(dismiss) { Icon(Icons.Rounded.Close, "닫기") }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf("전체", "찜한 세션").forEachIndexed { index, label ->
                    SegmentedButton(favoritesOnly == (index == 1), { favoritesOnly = index == 1 }, SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
                }
            }
            val all = timetable?.sessions.orEmpty()
            val shown = if (favoritesOnly) all.filter { it.id in favorites } else all
            when {
                phase == TimetablePhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                phase == TimetablePhase.FAILED -> DearbyErrorState("시간표를 불러오지 못했어요", "연결을 확인한 뒤 다시 시도해 주세요.", retry, Modifier.fillMaxSize())
                timetable == null || all.isEmpty() -> DearbyEmptyState("시간표가 없어요", "이 일정에는 아직 세션 시간표가 없어요.", Modifier.fillMaxSize())
                shown.isEmpty() -> DearbyEmptyState("찜한 세션이 없어요", "세션의 하트를 누르면 여기에 모여요.", Modifier.fillMaxSize(), Icons.Rounded.FavoriteBorder)
                else -> {
                    val tracks = timetable.tracks
                    val favored = all.filter { it.id in favorites }
                    val overlap: (SessionState) -> Int = { session -> if (session.id in favorites) favored.count { it.overlaps(session) } else 0 }
                    val card: @Composable (SessionState, Modifier) -> Unit = { session, modifier ->
                        SessionItem(session, tracks, session.id in favorites, overlap(session), { heart(session.id) }, { opened = session.id }, modifier)
                    }
                    val rows = remember(shown) { timetableRows(shown) }
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                        // 찜한 세션 = 나만의 시간표 (2026-10-26 사용자): one column in time order, no sideways scrolling.
                        if (favoritesOnly) MyTimetable(shown.map { MyTimetableEntry("", "", it, trackOf(it), tracks.indexOf(trackOf(it))) }, favorites, heart, { opened = it.id }, Modifier.padding(horizontal = 16.dp))
                        else if (tracks.isEmpty()) SingleColumn(rows, shown, card) else TrackGrid(rows, tracks, shown, card)
                    }
                }
            }
        }
    }
    timetable?.sessions?.firstOrNull { it.id == opened }?.let { session ->
        SessionSheet(session, trackOf(session), session.id in favorites, { heart(session.id) }) { opened = null }
    }
}

/** No tracks: a time band, then the slot's session card, one after another. */
@Composable private fun SingleColumn(rows: TimetableRows, sessions: List<SessionState>, card: @Composable (SessionState, Modifier) -> Unit) =
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.rows.forEachIndexed { index, (from, to) ->
            val starting = sessions.filter { rows.first[it.id] == index }
            if (starting.isEmpty()) return@forEachIndexed
            Text(slot(from, to), Modifier.padding(top = 8.dp).semantics { heading() }, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            starting.forEach { card(it, Modifier.fillMaxWidth()) }
        }
    }

/** Where a child of [TrackGrid] goes: rows [firstRow]..[lastRow], track columns [firstCol]..[lastCol]; column -1 is the pinned time column. */
private data class Cell(val firstRow: Int, val lastRow: Int, val firstCol: Int, val lastCol: Int)

/**
 * The table (contract "모바일 트랙 가로 넘기기", 2026-10-26): one sideways scroll for every row, so columns stay aligned, with the time
 * column placed at the scroll offset so it stays on the left. A row is as tall as its tallest one-row card (at least [MIN_ROW]); a card
 * spanning rows adds what it still needs to its last row. A slot a track has no session in stays an empty dark cell; a full-width
 * session (no track) spans every column. When every column fits at [MIN_COLUMN] the columns share the width and nothing scrolls.
 */
@Composable private fun TrackGrid(rows: TimetableRows, tracks: List<TrackState>, sessions: List<SessionState>, card: @Composable (SessionState, Modifier) -> Unit) =
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val column = maxOf(MIN_COLUMN, (maxWidth - TIME_COLUMN) / tracks.size)
        val scroll = rememberScrollState()
        val index = tracks.withIndex().associate { (i, track) -> track.id to i }
        val empty = MaterialTheme.colorScheme.surfaceContainerHighest
        val pinned = MaterialTheme.colorScheme.background
        Layout(content = {
            val covered = mutableSetOf<Pair<Int, Int>>()
            sessions.forEach { session ->
                val first = rows.first[session.id] ?: return@forEach
                val last = rows.last.getValue(session.id)
                // A track id the timetable does not list is drawn full width rather than dropped.
                val col = session.trackId?.let(index::get)
                val cols = if (col == null) 0..tracks.lastIndex else col..col
                (first..last).forEach { r -> cols.forEach { c -> covered += r to c } }
                card(session, Modifier.layoutId(Cell(first, last, cols.first, cols.last)).padding(4.dp))
            }
            rows.rows.indices.forEach { r ->
                tracks.indices.filter { (r to it) !in covered }.forEach { c ->
                    Box(Modifier.layoutId(Cell(r, r, c, c)).padding(4.dp).background(empty, RoundedCornerShape(10.dp)))
                }
                Box(Modifier.layoutId(Cell(r, r, -1, -1)).background(pinned).padding(start = 12.dp, top = 12.dp, end = 4.dp)) {
                    Text(slot(rows.rows[r].first, rows.rows[r].second).replace(" – ", "\n– "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
        }, modifier = Modifier.horizontalScroll(scroll)) { measurables, _ ->
            val columnPx = column.roundToPx(); val timePx = TIME_COLUMN.roundToPx()
            val cells = measurables.map { it.layoutId as Cell }
            fun width(cell: Cell) = if (cell.firstCol < 0) timePx else (cell.lastCol - cell.firstCol + 1) * columnPx
            val heights = IntArray(rows.rows.size) { MIN_ROW.roundToPx() }
            cells.indices.sortedBy { cells[it].lastRow - cells[it].firstRow }.forEach { i ->
                val cell = cells[i]
                val need = measurables[i].minIntrinsicHeight(width(cell))
                val have = (cell.firstRow..cell.lastRow).sumOf { heights[it] }
                if (need > have) heights[cell.lastRow] += need - have
            }
            val tops = heights.runningFold(0) { y, h -> y + h }
            val placeables = measurables.mapIndexed { i, measurable ->
                val cell = cells[i]
                measurable.measure(Constraints.fixed(width(cell), tops[cell.lastRow + 1] - tops[cell.firstRow]))
            }
            layout(timePx + tracks.size * columnPx, tops.last()) {
                placeables.forEachIndexed { i, placeable ->
                    val cell = cells[i]
                    if (cell.firstCol < 0) placeable.place(scroll.value, tops[cell.firstRow], zIndex = 1f)
                    else placeable.place(timePx + cell.firstCol * columnPx, tops[cell.firstRow])
                }
            }
        }
    }

/** A timetable session; the track colour follows `position` order. */
@Composable private fun SessionItem(session: SessionState, tracks: List<TrackState>, favorited: Boolean, overlap: Int, heart: () -> Unit, open: () -> Unit, modifier: Modifier) =
    SessionTile(session, tracks.firstOrNull { it.id == session.trackId }?.let { TrackBadge(it.name, it.room, trackColor(tracks.indexOf(it))) }, favorited, overlap, heart, open, modifier, null, false)

/** A session as a card, or a 쉬는시간·점심 band. */
@Composable private fun SessionTile(session: SessionState, track: TrackBadge?, favorited: Boolean, overlap: Int, heart: () -> Unit, open: () -> Unit,
    modifier: Modifier, time: String?, bigTrack: Boolean) {
    if (session.isBreak) return SessionBand(session.title, session.kind == "MEAL", modifier.clickable(onClick = open), time)
    SessionCard(session.title, track, session.tags, speakers(session), favorited, heart, open, modifier, time, overlap, bigTrack)
}

/** 설명·연사 (contract: a tap on a session opens its detail). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable internal fun SessionSheet(session: SessionState, track: TrackState?, favorited: Boolean, heart: () -> Unit, dismiss: () -> Unit) =
    ModalBottomSheet(dismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(listOfNotNull(slot(session.startAt, session.endAt), track?.name, track?.room).joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Text(session.title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            if (session.tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                session.tags.forEach { TagChip(it) }
            }
            if (session.speakers.isNotEmpty()) {
                Text("연사", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleSmall)
                session.speakers.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            session.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (!session.isBreak) FilledTonalButton(heart, Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(if (favorited) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (favorited) "찜 해제" else "찜하기")
            }
        }
    }

internal fun speakers(session: SessionState) = session.speakers.takeIf { it.isNotEmpty() }?.joinToString(", ")
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
/** `13:50 – 14:30` in the block's own offset. */
internal fun slot(from: OffsetDateTime, to: OffsetDateTime) = "${from.format(CLOCK)} – ${to.format(CLOCK)}"

private val TIME_COLUMN = 64.dp
private val MIN_COLUMN = 220.dp
private val MIN_ROW = 48.dp

/**
 * 나만의 시간표 (2026-10-26 사용자 "찜했다면 해당 찜한 시간표만 따로"): the 찜한 세션 in time order, one column. Each card leads with its
 * time and the track name and room (where to go). Between two cards: the free time and a move to another track, or — when they run at
 * the same time — a warning band (contract "찜 겹침": `a.startAt < b.endAt && b.startAt < a.endAt`).
 */
@Composable internal fun MyTimetable(entries: List<MyTimetableEntry>, favorites: Set<String>, heart: (String) -> Unit, open: (SessionState) -> Unit, modifier: Modifier = Modifier) =
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val sorted = entries.sortedBy { it.session.startAt.toInstant() }
        var latest: OffsetDateTime? = null
        var previous: MyTimetableEntry? = null
        sorted.forEach { entry ->
            val session = entry.session
            val before = previous
            val end = latest
            if (before != null && end != null) {
                if (end.toInstant() > session.startAt.toInstant()) OverlapBand()
                else Between(java.time.Duration.between(end.toInstant(), session.startAt.toInstant()).toMinutes(), before.track, entry.track)
            }
            val track = entry.track?.let { TrackBadge(it.name, it.room, trackColor(entry.trackPosition)) }
            SessionTile(session, track, session.id in favorites, sorted.count { it.session.overlaps(session) }, { heart(session.id) }, { open(session) },
                Modifier.fillMaxWidth(), slot(session.startAt, session.endAt), bigTrack = true)
            if (end == null || session.endAt.toInstant() > end.toInstant()) latest = session.endAt
            previous = entry
        }
    }

/** Free time before the next 찜한 세션, and the move when it is in another track (`빈 시간 20분 · 트랙 B · 302호로 이동`). */
@Composable private fun Between(minutes: Long, from: TrackState?, to: TrackState?) {
    val move = to?.takeIf { from != null && it.id != from.id }?.let { listOfNotNull(it.name, it.room).joinToString(" · ") + "(으)로 이동" }
    val free = if (minutes > 0) "빈 시간 " + listOfNotNull((minutes / 60).takeIf { it > 0 }?.let { "${it}시간" }, (minutes % 60).takeIf { it > 0 }?.let { "${it}분" }).joinToString(" ") else null
    val text = listOfNotNull(free, move).joinToString(" · ").ifEmpty { return }
    Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(if (move != null) Icons.AutoMirrored.Rounded.DirectionsWalk else Icons.Rounded.HourglassEmpty, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
    }
}

/** Two 찜한 세션 run at the same time (contract "찜 겹침"). */
@Composable private fun OverlapBand() = Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer, shape = RoundedCornerShape(10.dp)) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Rounded.WarningAmber, null, Modifier.size(18.dp))
        Text("같은 시간대에 찜한 세션이 겹쳐요", style = MaterialTheme.typography.labelLarge)
    }
}
