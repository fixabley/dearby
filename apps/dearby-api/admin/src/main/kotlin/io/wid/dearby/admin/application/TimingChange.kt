package io.wid.dearby.admin.application

import io.wid.dearby.domain.OverlapCheck
import io.wid.dearby.domain.Recurrence
import io.wid.dearby.domain.Schedule

// 모집·활동 기간 객체 안의 overlapCheck(native-v1.md "겹침 확인 시간")·recurrence(native-v1.md "반복 일정과 겹침 결과 묶음").
// has*가 true면 그 키를 보낸 것이고, 값이 null이면 지운다
data class TimingChange(
    val hasOverlapCheck: Boolean = false,
    val overlapCheck: OverlapCheck? = null,
    val hasRecurrence: Boolean = false,
    val recurrence: Recurrence? = null,
) {
    fun overlapOf(schedule: Schedule): OverlapCheck? = if (hasOverlapCheck) overlapCheck else schedule.overlapCheck
    fun recurrenceOf(schedule: Schedule): Recurrence? = if (hasRecurrence) recurrence else schedule.recurrence
}
