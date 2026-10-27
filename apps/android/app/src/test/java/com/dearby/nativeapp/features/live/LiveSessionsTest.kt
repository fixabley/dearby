package com.dearby.nativeapp.features.live

import com.dearby.nativeapp.entities.activity.model.Session
import com.dearby.nativeapp.entities.activity.model.SessionFavorite
import com.dearby.nativeapp.entities.activity.model.Track
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 행사 당일 진행 중 알림의 계획 로직 (2026-10-27 user decision: unit tests for the live plan with a fixed clock; the screen is checked through the Debug 미리보기).
 * Pure Kotlin on the JVM: [LiveSessions.plan], [LivePlan.from]·[LivePlan.until], [LiveSessions.frame], [LiveSessions.timeline] and [LiveSessions.nextAlarm]; the notification itself is not built here.
 */
class LiveSessionsTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val day = LocalDate.of(2026, 10, 27)
    private val trackA = Track("track-a", "트랙 A", "그랜드볼룸 A", 0)
    private val trackB = Track("track-b", "트랙 B", "그랜드볼룸 B", 1)

    /** Seoul `HH:mm` of [day] (or [date]) as epoch millis. */
    private fun at(time: String, date: LocalDate = day) = LocalTime.parse(time).atDate(date).atZone(seoul).toInstant().toEpochMilli()

    private fun favorite(id: String, start: String, end: String, track: Track?, title: String = id, activityId: String = "conf", date: LocalDate = day) = SessionFavorite(
        activityId, "컨퍼런스", "day-1", "1일차",
        Session(id, "day-1", track?.id, "TALK", title, null, LocalTime.parse(start).atDate(date).atOffset(seoul.rules.getOffset(day.atStartOfDay())),
            LocalTime.parse(end).atDate(date).atOffset(seoul.rules.getOffset(day.atStartOfDay())), emptyList(), emptyList()), track)

    /** 오늘 컨퍼런스의 찜: 키노트(트랙 없음) 09:30, 트랙 A 10:00, 트랙 B 11:00 — plus a 찜 of another activity and one of yesterday, which must not count. */
    private val favorites = listOf(
        favorite("talk-a", "10:00", "10:50", trackA, "모바일 라이브 업데이트"),
        favorite("keynote", "09:30", "09:50", null, "키노트"),
        favorite("talk-b", "11:00", "11:40", trackB, "서버 사이드 코틀린"),
        favorite("other", "10:00", "11:00", trackA, activityId = "other-conf"),
        favorite("yesterday", "10:00", "11:00", trackA, date = day.minusDays(1)))
    private val plan = LiveSessions.plan("conf", "컨퍼런스", favorites, day)!!

    @Test fun `plan keeps only the activity's 찜 of the day in start order, without track or room for a session spanning every track`() {
        assertEquals(listOf("keynote", "talk-a", "talk-b"), plan.entries.map { it.sessionId })
        assertEquals(listOf(null, "트랙 A", "트랙 B"), plan.entries.map { it.track })
        assertEquals(listOf(null, "그랜드볼룸 A", "그랜드볼룸 B"), plan.entries.map { it.room })
        assertEquals("컨퍼런스", plan.activityName)
        assertEquals("day-1", plan.entries.first().scheduleId)
    }

    @Test fun `without 찜 of the day there is no plan`() {
        assertNull(LiveSessions.plan("conf", "컨퍼런스", emptyList(), day))
        assertNull(LiveSessions.plan("conf", "컨퍼런스", favorites, day.plusDays(1)))
        assertNull(LiveSessions.plan("conf", "컨퍼런스", listOf(favorite("empty", "10:00", "10:00", trackA)), day))
    }

    @Test fun `the window runs from 30 minutes before the first 찜 to the latest end`() {
        assertEquals(at("09:00"), plan.from)
        assertEquals(at("11:40"), plan.until)
        assertNull(LiveSessions.frame(plan, at("08:59")))
        assertEquals("30분", LiveSessions.frame(plan, at("09:00"))?.chip)
        assertEquals("1분", LiveSessions.frame(plan, at("11:39"))?.chip)
        assertNull(LiveSessions.frame(plan, at("11:40")))
        // A long 찜 outlasting one that starts later keeps the window open until it ends.
        val long = LiveSessions.plan("conf", "컨퍼런스", listOf(favorite("long", "10:00", "12:00", trackA), favorite("short", "10:30", "11:00", trackB)), day)!!
        assertEquals(at("12:00"), long.until)
        assertEquals("long", LiveSessions.frame(long, at("11:30"))?.current?.sessionId)
    }

    @Test fun `during a session the title is its name, then the track and the time with the room, the chip its end`() {
        val frame = LiveSessions.frame(plan, at("10:30"))!!
        assertEquals("talk-a", frame.current?.sessionId)
        assertEquals("talk-b", frame.next?.sessionId)
        assertFalse(frame.moving)
        assertEquals("모바일 라이브 업데이트", frame.title)
        assertEquals(listOf("트랙 A", "10:00 – 10:50 · 그랜드볼룸 A"), frame.lines)
        assertEquals("20분", frame.chip)
        // A session spanning every track has neither line part.
        assertEquals(listOf("09:30 – 09:50"), LiveSessions.frame(plan, at("09:40"))!!.lines)
    }

    @Test fun `between sessions and before the first it is 이동, time first, to the room or else the track, the chip the time left`() {
        val frame = LiveSessions.frame(plan, at("10:50"))!!
        assertNull(frame.current)
        assertTrue(frame.moving)
        assertEquals("10분 뒤 시작 · 그랜드볼룸 B로 이동", frame.title)
        assertEquals(listOf("서버 사이드 코틀린", "트랙 B"), frame.lines)
        assertEquals("10분", frame.chip)
        // Before the first session too; a session spanning every track has no destination.
        val early = LiveSessions.frame(plan, at("09:10"))!!
        assertTrue(early.moving)
        assertEquals("20분 뒤 시작", early.title)
        assertEquals(listOf("키노트"), early.lines)
        // Without a room the track is the destination, and 으로 follows a final consonant other than ㄹ.
        val hall = LiveSessions.plan("conf", "컨퍼런스", listOf(favorite("x", "14:50", "15:30", Track("t", "본관 강당", null, 0), "오후 세션")), day)!!
        assertEquals("20분 뒤 시작 · 본관 강당으로 이동", LiveSessions.frame(hall, at("14:30"))!!.title)
    }

    @Test fun `the title reads 20분, 1시간 and 1시간 20분 while the chip is whole minutes until the end or the start`() {
        val two = LiveSessions.plan("conf", "컨퍼런스", listOf(favorite("early", "13:00", "13:10", trackA), favorite("late", "14:50", "16:00", trackB, "오후 세션")), day)!!
        fun frame(now: Long) = LiveSessions.frame(two, now)!!
        assertEquals("1시간 20분 뒤 시작 · 그랜드볼룸 B로 이동", frame(at("13:30")).title)
        assertEquals("80분", frame(at("13:30")).chip)
        assertEquals("1시간 뒤 시작 · 그랜드볼룸 B로 이동", frame(at("13:50")).title)
        assertEquals("60분", frame(at("13:50")).chip)
        assertEquals("20분", frame(at("14:30")).chip)
        // Seconds round up to the next minute, never under one.
        assertEquals("1분", frame(at("14:49") + 30_000).chip)
        assertEquals("1분 뒤 시작 · 그랜드볼룸 B로 이동", frame(at("14:49") + 30_000).title)
        // During a session: minutes until it ends, also past an hour.
        assertEquals("70분", frame(at("14:50")).chip)
        assertEquals("5분", frame(at("13:05")).chip)
    }

    @Test fun `two 찜 starting together are both in the plan, the first in 내 시간표 order is current and the other is not next`() {
        val pair = LiveSessions.plan("conf", "컨퍼런스", listOf(favorite("a", "13:00", "13:50", trackA, "A 발표"), favorite("b", "13:00", "13:50", trackB, "B 발표")), day)!!
        assertEquals(listOf("a", "b"), pair.entries.map { it.sessionId })
        val frame = LiveSessions.frame(pair, at("13:10"))!!
        assertEquals("a", frame.current?.sessionId)
        assertNull(frame.next)
        // Before they start the first is announced.
        assertEquals("20분 뒤 시작 · 그랜드볼룸 A로 이동", LiveSessions.frame(pair, at("12:40"))!!.title)
    }

    @Test fun `the track has a segment per session and 공백 in minutes, points at the inner boundaries and now's position`() {
        // 09:30–09:50 키노트, 공백 to 10:00, 10:00–10:50, 공백 to 11:00, 11:00–11:40.
        val line = LiveSessions.timeline(plan, at("10:55"))
        assertEquals(listOf(20 to true, 10 to false, 50 to true, 10 to false, 40 to true), line.segments)
        assertEquals(listOf(20, 30, 80, 90), line.points)
        assertEquals(85, line.position)
        assertEquals(0, LiveSessions.timeline(plan, at("09:10")).position)
        // Overlapping 찜 cut each other, and the overlap is one session segment.
        val long = LiveSessions.plan("conf", "컨퍼런스", listOf(favorite("long", "10:00", "12:00", trackA), favorite("short", "10:30", "11:00", trackB)), day)!!
        assertEquals(listOf(30 to true, 30 to true, 60 to true), LiveSessions.timeline(long, at("10:40")).segments)
        // Below Android 16, one line of steps.
        assertEquals("●●○", LiveSessions.steps(plan, at("10:55")))
        assertEquals("●◉○", LiveSessions.steps(plan, at("10:30")))
    }

    @Test fun `the next alarm is the window start, then every whole minute before the end or start the chip counts to, none after the end`() {
        assertEquals(at("09:00"), LiveSessions.nextAlarm(plan, at("08:00")))
        assertEquals(at("09:01"), LiveSessions.nextAlarm(plan, at("09:00")))  // 이동 before the keynote: every minute
        assertEquals(at("09:01"), LiveSessions.nextAlarm(plan, at("09:00") + 20_000))  // on the minute before the start, so `n분` turns exactly
        assertEquals(at("10:01"), LiveSessions.nextAlarm(plan, at("10:00")))  // in a session too
        assertEquals(at("10:50"), LiveSessions.nextAlarm(plan, at("10:49")))  // it ends and 이동 begins
        assertEquals(at("10:51"), LiveSessions.nextAlarm(plan, at("10:50")))
        assertEquals(at("11:40"), LiveSessions.nextAlarm(plan, at("11:39")))  // the last end
        assertNull(LiveSessions.nextAlarm(plan, at("11:40")))
    }

    @Test fun `the preview shows 세션 중 or 이동 at once`() {
        val now = at("10:00")
        assertFalse(LiveSessions.frame(LiveSessions.preview(false, now), now)!!.moving)
        assertEquals("12분 뒤 시작 · 그랜드볼룸 B로 이동", LiveSessions.frame(LiveSessions.preview(true, now), now)!!.title)
    }
}
