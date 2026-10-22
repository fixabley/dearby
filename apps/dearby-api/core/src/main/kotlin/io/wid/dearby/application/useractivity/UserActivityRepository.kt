package io.wid.dearby.application.useractivity

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ProgressKind
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.useractivity.UserActivity

// 저장소 포트. 구현은 adaptor/persistence
interface UserActivityRepository {

    // 만든 순서. 게시 활동이 아니면(지워졌거나 내려감) deleted
    fun findAll(userId: UserId): List<UserActivity>

    fun find(userId: UserId, activityId: Id): UserActivity?

    // 없을 때만 만든다. 만들었으면 true. 참여 확정 표시는 없다(일정별 상태로 추론, 2026-10-22)
    fun insertIfAbsent(userId: UserId, activityId: Id): Boolean

    // 일정 상태도 함께 지운다
    fun delete(userId: UserId, activityId: Id)

    // statusId(어드민 상태)나 defaultKind(기본 상태) 중 하나
    fun pick(userId: UserId, activityId: Id, scheduleId: Id, statusId: Id?, defaultKind: ProgressKind?)
}
