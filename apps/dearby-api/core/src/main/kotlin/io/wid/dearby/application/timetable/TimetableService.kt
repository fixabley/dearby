package io.wid.dearby.application.timetable

import io.wid.dearby.application.activity.ActivityCurationRepository
import io.wid.dearby.application.activity.ActivityService
import io.wid.dearby.application.activity.ScheduleSlot
import io.wid.dearby.application.tag.TagRepository
import io.wid.dearby.domain.Activity
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.Schedule
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.timetable.Session
import io.wid.dearby.domain.timetable.Timetable
import io.wid.dearby.domain.timetable.TimetableInput
import io.wid.dearby.domain.timetable.TimetableRules
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

// 트랙·세션 시간표와 세션 찜(native-v1.md "트랙·세션 시간표"). 시간표는 일정 블록 하나의 하위 시간표다.
// 세션 찜은 로그인만이고 활동 즐겨찾기·알림·추천 신호와 따로다(부수 효과 없음)
@Service
class TimetableService(
    private val timetables: TimetableRepository,
    private val activities: ActivityService,
    private val curations: ActivityCurationRepository,
    private val tags: TagRepository,
) {

    // 게시 활동의 일정(모집 기간 제외)에 세션이 있을 때만. 아니면 404
    @Transactional(readOnly = true)
    fun timetable(activityId: Id, scheduleId: Id): ScheduledTimetable {
        val activity = activities.getById(activityId).activity
        val schedule = blocks(activity).firstOrNull { it.id == scheduleId } ?: throw notFound(scheduleId)
        val timetable = timetables.find(scheduleId)
        if (timetable.sessions.isEmpty()) throw notFound(scheduleId)
        return ScheduledTimetable(activity, schedule, timetable)
    }

    // 어드민: 통째 저장. 빈 목록이면 시간표를 지운다. 모집 기간·반복·날짜 미정 일정과 블록 밖 세션은 400
    @Transactional
    fun replace(activityId: Id, scheduleId: Id, input: TimetableInput): Timetable {
        val stored = curations.findSchedule(activityId, scheduleId) ?: throw notFound(scheduleId)
        if (stored.slot == ScheduleSlot.APPLICATION.name) throw InvalidInputException("모집 기간에는 시간표를 붙이지 않습니다")
        val cleaned = if (input.isEmpty) input else TimetableRules.validate(input, TimetableRules.window(stored.schedule))
        val tagIds = cleaned.sessions.flatMap { it.tagIds }.toSet()
        val known = tags.findAllById(tagIds).map { it.id }.toSet()
        (tagIds - known).firstOrNull()?.let { throw InvalidInputException("없는 태그입니다: $it") }
        timetables.replace(scheduleId, cleaned)
        return timetables.find(scheduleId)
    }

    @Transactional(readOnly = true)
    fun adminTimetable(activityId: Id, scheduleId: Id): Timetable {
        curations.findSchedule(activityId, scheduleId) ?: throw notFound(scheduleId)
        return timetables.find(scheduleId)
    }

    @Transactional(readOnly = true)
    fun favoriteIds(userId: UserId): Set<Id> = timetables.favorites(userId).keys

    // 게시 활동의 지우지 않은 일정의 세션만(아니면 404). 이미 있으면 그대로(멱등)
    @Transactional
    fun putFavorite(userId: UserId, sessionId: Id) {
        val owner = timetables.sessionOwner(sessionId) ?: throw sessionNotFound(sessionId)
        runCatching { activities.getById(owner.activityId) }.getOrNull() ?: throw sessionNotFound(sessionId)
        timetables.saveFavorite(userId, sessionId, OffsetDateTime.now(ZoneOffset.UTC))
    }

    @Transactional
    fun deleteFavorite(userId: UserId, sessionId: Id) = timetables.deleteFavorite(userId, sessionId)

    // 내 시간표: 찜한 세션을 시작 순으로. 게시되지 않았거나 지워진 일정의 세션은 뺀다(행은 남긴다)
    @Transactional(readOnly = true)
    fun favorites(userId: UserId): List<FavoriteSession> {
        val favoritedAt = timetables.favorites(userId)
        val sessions = timetables.sessions(favoritedAt.keys)
        val owners = timetables.owners(sessions.map { it.id })
        val listings = activities.publishedListings(ids = owners.values.map { it.activityId }.toSet()).associateBy { it.activity.id }
        return sessions.mapNotNull { session ->
            val owner = owners[session.id] ?: return@mapNotNull null
            val activity = listings[owner.activityId]?.activity ?: return@mapNotNull null
            val schedule = blocks(activity).firstOrNull { it.id == owner.scheduleId } ?: return@mapNotNull null
            FavoriteSession(activity, schedule, session, favoritedAt.getValue(session.id))
        }.sortedWith(compareBy({ it.session.startAt }, { it.session.id }))
    }

    private fun blocks(activity: Activity): List<Schedule> = listOf(activity.activitySchedule) + activity.schedules

    private fun notFound(scheduleId: Id) = NotFoundException("시간표를 찾을 수 없습니다", "Timetable not found: schedule $scheduleId")
    private fun sessionNotFound(sessionId: Id) = NotFoundException("세션을 찾을 수 없습니다", "Session not found: $sessionId")
}

data class ScheduledTimetable(val activity: Activity, val schedule: Schedule, val timetable: Timetable)

data class FavoriteSession(val activity: Activity, val schedule: Schedule, val session: Session, val favoritedAt: OffsetDateTime)
