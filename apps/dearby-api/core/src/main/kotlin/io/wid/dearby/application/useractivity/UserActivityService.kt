package io.wid.dearby.application.useractivity

import io.wid.dearby.application.activity.ActivityRepository
import io.wid.dearby.domain.*
import io.wid.dearby.domain.useractivity.UserActivity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 내 활동 서버 저장(native-v1.md "내 활동 서버 저장·일정 상태"). 로그인 사용자만
@Service
class UserActivityService(
    private val userActivities: UserActivityRepository,
    private val activities: ActivityRepository,
) {

    @Transactional(readOnly = true)
    fun list(userId: UserId): List<UserActivity> = userActivities.findAll(userId)

    // 게시 활동만 넣는다(아니면 404). 이미 있으면 그대로
    @Transactional
    fun put(userId: UserId, activityId: Id): UserActivity {
        requirePublished(activityId)
        userActivities.insertIfAbsent(userId, activityId)
        return userActivities.find(userId, activityId)!!
    }

    @Transactional
    fun delete(userId: UserId, activityId: Id) = userActivities.delete(userId, activityId)

    // statusId는 그 일정의 어드민 상태여야 하고, kind는 그 일정이 기본 상태를 쓸 때만(그 밖은 400). 아직 내 활동에 없으면 함께 넣는다
    @Transactional
    fun pick(userId: UserId, activityId: Id, scheduleId: Id, statusId: Id?, kind: ProgressKind?): UserActivity {
        val activity = requirePublished(activityId)
        val schedule = activity.calendarCandidates.firstOrNull { it.id == scheduleId }
            ?: throw InvalidInputException("이 활동의 일정이 아닙니다", "Schedule $scheduleId not in activity $activityId")
        val status = when {
            statusId != null && kind != null -> throw InvalidInputException("statusId와 kind 중 하나만 보내 주세요")
            statusId != null -> schedule.statuses.firstOrNull { it.id == statusId }
            kind != null -> schedule.statuses.firstOrNull { it.id == null && it.kind == kind }
            else -> throw InvalidInputException("statusId나 kind가 필요합니다")
        } ?: throw InvalidInputException("이 일정에서 고를 수 있는 상태가 아닙니다", "Status ${statusId ?: kind} not in schedule $scheduleId")
        userActivities.insertIfAbsent(userId, activityId)
        userActivities.pick(userId, activityId, scheduleId, status.id, if (status.id == null) status.kind else null)
        return userActivities.find(userId, activityId)!!
    }

    // 기기에만 있던 기록을 로그인 뒤 한 번 올린다. 이미 있는 활동은 서버 값을 두고, 게시 활동이 아니거나 이 활동의 일정이 아닌 것은 건너뛴다.
    // 기기는 kind만 알므로 어드민 상태가 있는 일정은 같은 종류의 첫 상태로 옮긴다(없으면 버린다)
    @Transactional
    fun import(userId: UserId, items: List<ImportItem>): List<UserActivity> {
        if (items.size > MAX_IMPORT) throw InvalidInputException("한 번에 ${MAX_IMPORT}개까지 올릴 수 있습니다")
        items.distinctBy { it.activityId }.forEach { item ->
            val activity = activities.findById(item.activityId) ?: return@forEach
            if (!userActivities.insertIfAbsent(userId, activity.id)) return@forEach
            item.statuses.forEach { (scheduleId, kind) ->
                val status = activity.calendarCandidates.firstOrNull { it.id == scheduleId }?.statuses?.firstOrNull { it.kind == kind }
                status?.let { userActivities.pick(userId, activity.id, scheduleId, it.id, if (it.id == null) it.kind else null) }
            }
        }
        return userActivities.findAll(userId)
    }

    private fun requirePublished(activityId: Id): Activity =
        activities.findById(activityId) ?: throw NotFoundException("활동을 찾을 수 없습니다", "Activity not found: $activityId")

    data class ImportItem(val activityId: Id, val statuses: List<Pair<Id, ProgressKind>>)

    private companion object {
        const val MAX_IMPORT = 500
    }
}
