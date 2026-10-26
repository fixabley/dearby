package io.wid.dearby.application.activity

import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.OverlapCheck
import io.wid.dearby.domain.Recurrence
import io.wid.dearby.domain.Schedule
import io.wid.dearby.domain.ScheduleKind
import io.wid.dearby.domain.SchedulePhase
import io.wid.dearby.domain.ScheduleStatus
import io.wid.dearby.domain.ScheduleTime
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

// 세부 일정의 부분 변경(admin-v1.md "세부 일정 편집기"). null은 "보내지 않음"이고, 만들 때는 existing이 없다
data class ScheduleItemPatch(
    val title: String?,
    val kind: ScheduleKind?,
    val status: ScheduleStatus?,
    val hasTime: Boolean, // startAt·endAt·startDate·endDate 중 하나라도 왔으면 시각·날짜 묶음을 통째로 바꾼다
    val startAt: OffsetDateTime?,
    val endAt: OffsetDateTime?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val timeZone: ZoneId?,
    val dateLabel: String?,
    val note: String?,
    val hasOverlapCheck: Boolean = false, // overlapCheck가 왔으면(null 포함) 바꾼다(native-v1.md "겹침 확인 시간")
    val overlapCheck: OverlapCheck? = null,
    val hasRecurrence: Boolean = false, // recurrence가 왔으면(null 포함) 바꾼다(native-v1.md "반복 일정과 겹침 결과 묶음")
    val recurrence: Recurrence? = null,
    val hasPhase: Boolean = false, // phase가 왔으면(null 포함) 바꾼다. null은 저장값을 지워 자동 판정으로 되돌린다(admin-v1.md "phase")
    val phase: SchedulePhase? = null, // 모집·활동 기간 행은 고정이라 이 필드가 오면 timingOnly가 아니다
) {
    // 겹침 확인 시간·반복 규칙만 바꾸는 요청. 모집·활동 기간 행은 이것만 이 편집기로 고칠 수 있다
    val timingOnly: Boolean get() = (hasOverlapCheck || hasRecurrence) && !hasTime && !hasPhase && listOf(title, kind, status, timeZone, dateLabel, note).all { it == null }

    // 보낸 값은 바꾸고 아니면 기존 값. 반복이 남으면 날짜·겹침 확인 시간과 맞는지 본다
    fun timing(existing: Schedule?, time: ScheduleTime?): Pair<OverlapCheck?, Recurrence?> {
        val overlap = if (hasOverlapCheck) overlapCheck else existing?.overlapCheck
        val rule = if (hasRecurrence) recurrence else existing?.recurrence
        rule?.requireFits(time, overlap)
        return overlap to rule
    }

    // 날짜·시각 규칙은 일정 직접 지정(ScheduleEdit)과 같다: 섞으면 400, 끝이 앞서면 400. 시각도 날짜도 없으면 dateLabel이 있어야 한다
    fun toItem(existing: Schedule?): ScheduleItem {
        val title = (title ?: existing?.title)?.ifEmpty { null } ?: throw InvalidInputException("제목을 입력해 주세요")
        if (title.length > 200) throw InvalidInputException("제목은 200자까지입니다")
        val kind = kind ?: existing?.kind ?: throw InvalidInputException("kind는 DEADLINE·ANNOUNCEMENT·INTERVIEW·EVENT 중 하나입니다")
        val status = status ?: existing?.status ?: ScheduleStatus.CONFIRMED
        var time = if (hasTime) ScheduleEdit(startDate, endDate, startAt, endAt).toTime() else existing?.time
        timeZone?.let { zone -> (time as? ScheduleTime.Timed)?.let { time = ScheduleTime.Timed(it.start?.withZoneSameInstant(zone), it.end?.withZoneSameInstant(zone)) } }
        val dateLabel = dateLabel ?: existing?.dateLabel ?: ""
        if (time == null && dateLabel.isEmpty()) throw InvalidInputException("날짜나 원문 문구 중 하나는 입력해 주세요")
        val note = (if (this.note != null) this.note.ifEmpty { null } else existing?.curatedNote)
        if (note != null && note.length > 300) throw InvalidInputException("사유는 300자까지입니다")
        val (overlap, rule) = timing(existing, time)
        val phase = if (hasPhase) phase else existing?.storedPhase
        return ScheduleItem(title, kind, status, time, dateLabel, note, overlap, rule, phase)
    }
}
