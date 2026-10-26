package com.dearby.nativeapp.pages.activity

import com.dearby.nativeapp.shared.ui.TagLabel
import java.time.OffsetDateTime

enum class TimetablePhase { LOADING, FAILED, MISSING, LOADED }

/** One block's 트랙·세션 시간표 as shown (contract "트랙·세션 시간표"): [tracks] in `position` order, [sessions] start-first. */
data class TimetableState(val scheduleTitle: String, val tracks: List<TrackState>, val sessions: List<SessionState>)
data class TrackState(val id: String, val name: String, val room: String?)
/**
 * A session; [trackId] null spans every track. [speakers] are `이름 · 소속` lines, [tags] at most 5 분야 tags. Times keep the block's offset.
 * [kind] is TALK, KEYNOTE, BREAK, MEAL or OTHER.
 */
data class SessionState(
    val id: String, val trackId: String?, val kind: String, val title: String, val description: String?,
    val startAt: OffsetDateTime, val endAt: OffsetDateTime, val speakers: List<String>, val tags: List<TagLabel>,
) {
    /** 쉬는시간·점심: drawn as a band, without a heart. */
    val isBreak get() = kind == "BREAK" || kind == "MEAL"
    /** A 하루 시간표 row standing for a whole block (no sessions, or its timetable could not be read): a band without a heart or a detail. */
    val isBlock get() = kind == BLOCK_KIND || kind == BLOCK_FAILED_KIND
    /** Contract "찜 겹침": `a.startAt < b.endAt && b.startAt < a.endAt`. */
    fun overlaps(other: SessionState) = startAt.toInstant() < other.endAt.toInstant() && other.startAt.toInstant() < endAt.toInstant()
}
internal const val BLOCK_KIND = "BLOCK"
internal const val BLOCK_FAILED_KIND = "BLOCK_FAILED"

/**
 * One timed block of a day (하루 시간표, 2026-10-26 사용자 "10/28일 누르면 시간표보기"): the detail's or 진행 단계's row as the day sheet reads it.
 * [scheduleId] is null for a 진행 단계 row without a timetable (it is drawn by [title] alone). Times are Seoul.
 */
data class DayBlock(val scheduleId: String?, val title: String, val startAt: OffsetDateTime, val endAt: OffsetDateTime, val hasTimetable: Boolean)
/** A block's timetable read for the day sheet; a block without one is MISSING from the start and never fetched. */
data class DayBlockLoad(val block: DayBlock, val phase: TimetablePhase, val timetable: TimetableState?)

/**
 * The day's blocks as one table: tracks are the union of every block's tracks **by name** (first seen first; a block's track id maps to
 * the first track of that name, whose room stands for all), sessions keep their heart. A block without a timetable (키노트·쉬는시간, or 404)
 * is one full-width row with its title; a block whose read failed is `{title} · 불러오지 못했어요`.
 */
fun dayTimetable(loads: List<DayBlockLoad>): TimetableState {
    val tracks = mutableListOf<TrackState>()
    val sessions = loads.flatMap { (block, phase, timetable) ->
        if (timetable == null || timetable.sessions.isEmpty()) {
            val failed = phase == TimetablePhase.FAILED
            return@flatMap listOf(SessionState("block:${block.scheduleId ?: block.startAt}", null, if (failed) BLOCK_FAILED_KIND else BLOCK_KIND,
                if (failed) "${block.title} · 불러오지 못했어요" else block.title, null, block.startAt, block.endAt, emptyList(), emptyList()))
        }
        val merged = timetable.tracks.associate { track ->
            track.id to (tracks.firstOrNull { it.name == track.name } ?: track.also { tracks += it }).id
        }
        timetable.sessions.map { it.copy(trackId = it.trackId?.let { id -> merged[id] ?: id }) }
    }.sortedBy { it.startAt.toInstant() }
    return TimetableState("", tracks, sessions)
}

/**
 * One card of 나만의 시간표 (2026-10-26 사용자 "찜한 시간표만 따로"): a 찜한 세션 with its activity, track and the track's palette index.
 * [track] is null for a full-width session or when the track could not be read.
 */
data class MyTimetableEntry(val activityId: String, val activityName: String, val session: SessionState, val track: TrackState?, val trackPosition: Int)

/**
 * The table's time rows (contract "행 나누기"): every distinct start and end instant of the sessions cuts the time, and an interval no
 * session covers is dropped, so non-overlapping pairs give one row each and tracks with different boundaries split into finer rows that a
 * longer session spans. [first] and [last] are the row indexes (inclusive) a session spans, by session id.
 */
data class TimetableRows(val rows: List<Pair<OffsetDateTime, OffsetDateTime>>, val first: Map<String, Int>, val last: Map<String, Int>)

fun timetableRows(sessions: List<SessionState>): TimetableRows {
    val cuts = sessions.flatMap { listOf(it.startAt, it.endAt) }.distinctBy { it.toInstant() }.sortedBy { it.toInstant() }
    fun SessionState.covers(row: Pair<OffsetDateTime, OffsetDateTime>) = startAt.toInstant() <= row.first.toInstant() && endAt.toInstant() >= row.second.toInstant()
    val rows = cuts.zipWithNext().filter { row -> sessions.any { it.covers(row) } }
    val first = mutableMapOf<String, Int>(); val last = mutableMapOf<String, Int>()
    sessions.forEach { session ->
        val covered = rows.indices.filter { session.covers(rows[it]) }
        if (covered.isNotEmpty()) { first[session.id] = covered.first(); last[session.id] = covered.last() }
    }
    return TimetableRows(rows, first, last)
}
