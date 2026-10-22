package io.wid.dearby.domain.useractivity

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ProgressKind
import java.time.OffsetDateTime

// 내 활동에 넣은 활동(native-v1.md "내 활동 서버 저장"). activityId는 논리키라 활동이 지워지거나 내려가면 deleted
data class UserActivity(
    val activityId: Id,
    val createdAt: OffsetDateTime,
    val deleted: Boolean,
    val statuses: List<SchedulePick>,
)

// 일정마다 고른 상태. statusId가 null이면 기본 상태(kind)
data class SchedulePick(val scheduleId: Id, val statusId: Id?, val kind: ProgressKind)
