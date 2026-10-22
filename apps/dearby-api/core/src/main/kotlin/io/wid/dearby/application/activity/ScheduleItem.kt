package io.wid.dearby.application.activity

import io.wid.dearby.domain.OverlapCheck
import io.wid.dearby.domain.Schedule
import io.wid.dearby.domain.ScheduleKind
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
)

// 저장된 일정과 그 슬롯. slot이 있으면 모집·활동 기간이라 세부 일정 편집기로는 고치지 않는다
data class StoredSchedule(val schedule: Schedule, val slot: String?)
