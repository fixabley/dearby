package com.dearby.nativeapp.features.live

import com.dearby.nativeapp.entities.activity.model.Session
import com.dearby.nativeapp.entities.activity.model.SessionFavorite
import com.dearby.nativeapp.entities.activity.model.Track
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 행사 당일 진행 중 알림의 계획 로직 (2026-10-27 user decision: unit tests for the live plan with a fixed clock; the screen is checked through the Debug 미리보기).
 * Pure Kotlin on the JVM: [LiveSessions.plan], [LivePlan.from]·[LivePlan.until], [LiveSessions.frame] and [LiveSessions.nextAlarm]; the notification itself is not built here.
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

    @Test fun `plan keeps only the activity's 찜 of the day in start order, without a place for a session spanning every track`() {
        assertEquals(listOf("keynote", "talk-a", "talk-b"), plan.entries.map { it.sessionId })
        assertEquals(listOf(null, "트랙 A · 그랜드볼룸 A", "트랙 B · 그랜드볼룸 B"), plan.entries.map { it.place })
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
        assertEquals("09:30", LiveSessions.frame(plan, at("09:00"))?.chip)
        assertEquals("~11:40", LiveSessions.frame(plan, at("11:39"))?.chip)
        assertNull(LiveSessions.frame(plan, at("11:40")))
        // A long 찜 outlasting one that starts later keeps the window open until it ends.
        val long = LiveSessions.plan("conf", "컨퍼런스", listOf(favorite("long", "10:00", "12:00", trackA), favorite("short", "10:30", "11:00", trackB)), day)!!
        assertEquals(at("12:00"), long.until)
        assertEquals("long", LiveSessions.frame(long, at("11:30"))?.current?.sessionId)
    }

    @Test fun `during a session the body says 지금 and the next with its start and the time left`() {
        val frame = LiveSessions.frame(plan, at("10:30"))!!
        assertEquals("talk-a", frame.current?.sessionId)
        assertEquals("talk-b", frame.next?.sessionId)
        assertEquals(listOf("지금 · 트랙 A · 그랜드볼룸 A · 모바일 라이브 업데이트", "다음 11:00 · 트랙 B · 그랜드볼룸 B · 서버 사이드 코틀린 · 30분 뒤"), frame.lines)
        assertEquals("~10:50", frame.chip)
        assertEquals(60, frame.progress)
    }

    @Test fun `between sessions only the next is shown, without progress, with the chip at its start`() {
        val frame = LiveSessions.frame(plan, at("09:10"))!!
        assertNull(frame.current)
        assertEquals(listOf("다음 09:30 · 키노트 · 20분 뒤"), frame.lines)
        assertEquals("09:30", frame.chip)
        assertNull(frame.progress)
        // After the last session started nothing is next.
        assertEquals(listOf("지금 · 트랙 B · 그랜드볼룸 B · 서버 사이드 코틀린"), LiveSessions.frame(plan, at("11:20"))!!.lines)
    }

    @Test fun `time left reads 20분, 1시간 and 1시간 20분`() {
        val afternoon = LiveSessions.plan("conf", "컨퍼런스", listOf(favorite("late", "14:50", "15:30", trackB, "오후 세션")), day)!!
        fun line(now: String) = LiveSessions.frame(afternoon, at(now))!!.lines.single()
        assertEquals("다음 14:50 · 트랙 B · 그랜드볼룸 B · 오후 세션 · 20분 뒤", line("14:30"))
        // 60 minutes and more read in hours, so the window must start earlier than 30 minutes before for these; use a plan with an earlier 찜.
        val two = LiveSessions.plan("conf", "컨퍼런스", listOf(favorite("early", "13:00", "13:10", trackA), favorite("late", "14:50", "15:30", trackB, "오후 세션")), day)!!
        assertEquals("다음 14:50 · 트랙 B · 그랜드볼룸 B · 오후 세션 · 1시간 20분 뒤", LiveSessions.frame(two, at("13:30"))!!.lines.last())
        assertEquals("다음 14:50 · 트랙 B · 그랜드볼룸 B · 오후 세션 · 1시간 뒤", LiveSessions.frame(two, at("13:50"))!!.lines.last())
        // Seconds round up to the next minute, never under one.
        assertEquals("다음 14:50 · 트랙 B · 그랜드볼룸 B · 오후 세션 · 1분 뒤", LiveSessions.frame(two, at("14:49") + 30_000)!!.lines.last())
    }

    @Test fun `two 찜 starting together are both in the plan, the first in 내 시간표 order is 지금 and the other is not 다음`() {
        val pair = LiveSessions.plan("conf", "컨퍼런스", listOf(favorite("a", "13:00", "13:50", trackA, "A 발표"), favorite("b", "13:00", "13:50", trackB, "B 발표")), day)!!
        assertEquals(listOf("a", "b"), pair.entries.map { it.sessionId })
        assertEquals(at("12:30"), pair.from)
        val frame = LiveSessions.frame(pair, at("13:10"))!!
        assertEquals("a", frame.current?.sessionId)
        assertNull(frame.next)
        assertEquals(listOf("지금 · 트랙 A · 그랜드볼룸 A · A 발표"), frame.lines)
        // Before they start both are 다음 candidates; the first is announced once.
        assertEquals(listOf("다음 13:00 · 트랙 A · 그랜드볼룸 A · A 발표 · 20분 뒤"), LiveSessions.frame(pair, at("12:40"))!!.lines)
    }

    @Test fun `the next alarm is the window start, then the nearest start, end, 10 minutes before a start or 5 minutes on, and none after the end`() {
        assertEquals(at("09:00"), LiveSessions.nextAlarm(plan, at("08:00")))
        assertEquals(at("09:05"), LiveSessions.nextAlarm(plan, at("09:00")))  // 5 minutes on
        assertEquals(at("09:20"), LiveSessions.nextAlarm(plan, at("09:17")))  // 10 minutes before the keynote
        assertEquals(at("09:30"), LiveSessions.nextAlarm(plan, at("09:26")))  // the keynote starts
        assertEquals(at("09:50"), LiveSessions.nextAlarm(plan, at("09:46")))  // it ends (also 10 minutes before 트랙 A)
        assertEquals(at("10:05"), LiveSessions.nextAlarm(plan, at("10:00")))  // a start now is not "after now"
        assertEquals(at("11:40"), LiveSessions.nextAlarm(plan, at("11:39")))  // the last end
        assertNull(LiveSessions.nextAlarm(plan, at("11:40")))
    }
}
