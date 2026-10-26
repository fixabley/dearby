package io.wid.dearby.application.activity

import io.wid.dearby.domain.OverlapCheck
import io.wid.dearby.domain.Recurrence
import io.wid.dearby.domain.Schedule
import io.wid.dearby.domain.ScheduleKind
import io.wid.dearby.domain.SchedulePhase
import io.wid.dearby.domain.ScheduleStatus
import io.wid.dearby.domain.ScheduleTime

// 운영자가 만들거나 고치는 세부 일정(슬롯 없음, admin-v1.md "세부 일정 편집기"). time이 null이면 dateLabel 문구만 남는다
data class ScheduleItem(
    val title: String,
    val kind: ScheduleKind,
    val status: ScheduleStatus,
    val time: ScheduleTime?,
    val dateLabel: String,
    val note: String?, // 사유(≤300자)
    val overlapCheck: OverlapCheck? = null, // 겹침 확인 시간(native-v1.md "겹침 확인 시간")
    val recurrence: Recurrence? = null, // 반복 규칙(native-v1.md "반복 일정과 겹침 결과 묶음")
    val phase: SchedulePhase? = null, // 저장할 선발 과정·활동 일정 값. null이면 저장값 없음(읽을 때 자동 판정)
)

// 저장된 일정과 그 슬롯. slot이 있으면 모집·활동 기간이라 세부 일정 편집기로는 고치지 않는다
data class StoredSchedule(val schedule: Schedule, val slot: String?)
