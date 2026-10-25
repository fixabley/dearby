package io.wid.dearby.batch.push

import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.activity.ActivityService
import io.wid.dearby.application.favorite.FavoriteRepository
import io.wid.dearby.application.push.PushRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ScheduleStatus
import io.wid.dearby.domain.ScheduleTime
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.favorite.FavoriteTarget
import io.wid.dearby.domain.favorite.NotifyLevel
import io.wid.dearby.domain.push.PushDelivery
import io.wid.dearby.domain.push.PushDevice
import io.wid.dearby.domain.push.PushKind
import io.wid.dearby.domain.push.PushPlatform
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.DateTimeException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// 즐겨찾기 알림(native-v1.md "프로그램·활동별 알림 수준"): 매시 돌며 기기 시간대가 아침 8시인 기기에 그날 알림 거리를 보낸다.
// 거리: 신규 항목(즐겨찾기 프로그램에 새로 게시되어 열린 활동, 한 번만) · 신청일(모집 시작 당일·마감 하루 전·마감 당일) · 전체면 그 밖의 일정 당일.
// 활동 단위 수준이 프로그램 수준보다 앞서고, 전체 스위치(notification_settings)가 꺼지면 보내지 않는다. 거리가 3개를 넘으면 "오늘 일정 n개" 하나로 묶는다.
// (사용자, 활동, 종류, 그 기기 날짜)마다 한 번(push_deliveries). 토큰은 로그에 남기지 않는다
@Service
class FavoritePushService(
    private val push: PushRepository,
    private val favorites: FavoriteRepository,
    private val activities: ActivityService,
    private val sender: PushSender,
    private val properties: PushProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    data class Report(val devices: Int, val notifications: Int)

    // anyHour: --send-push-now(수동 실행)는 기기 시각과 상관없이 지금 보낸다. 같은 날 다시 보내지는 않는다
    fun send(now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC), anyHour: Boolean = false): Report {
        val due = push.devices().filter { anyHour || now.atZoneSameInstant(zoneOf(it)).hour == SEND_HOUR }
        if (due.isEmpty()) return Report(0, 0)
        val problems = PushPlatform.entries.associateWith { sender.problem(it) }
        problems.forEach { (platform, problem) ->
            if (problem != null && due.any { it.platform == platform }) log.info("Push to {} skipped: {}", platform, problem)
        }
        val ready = due.filter { problems[it.platform] == null }
        val disabled = push.disabledUsers(ready.map { it.userId }.toSet())
        val byUser = ready.filter { it.userId !in disabled }.groupBy { it.userId }
        val fresh = favorites.newRecruitments(byUser.keys, now)
        var sent = 0
        byUser.forEach { (userId, devices) ->
            runCatching { sent += sendTo(userId, devices, fresh[userId].orEmpty().values.flatten().toSet(), now) }
                .onFailure { log.warn("Push failed for a user", it) }
        }
        log.info("Push: {} devices due, {} ready, {} notifications sent", due.size, ready.size, sent)
        return Report(due.size, sent)
    }

    private fun sendTo(userId: UserId, devices: List<PushDevice>, fresh: Set<Id>, now: OffsetDateTime): Int {
        val all = favorites.findAll(userId)
        val programLevels = all.filter { it.target == FavoriteTarget.PROGRAM }.associate { it.targetId to it.notifyLevel }
        val activityLevels = all.filter { it.target == FavoriteTarget.ACTIVITY }.associate { it.targetId to it.notifyLevel }
        val listings = (activities.publishedListings(ids = activityLevels.keys) + activities.publishedListings(programIds = programLevels.keys))
            .distinctBy { it.activity.id }
        val delivered = push.deliveries(userId, listings.map { it.activity.id }).toMutableList()
        var count = 0
        devices.groupBy { zoneOf(it) }.forEach { (zone, group) ->
            val today = now.atZoneSameInstant(zone).toLocalDate()
            val items = listings.flatMap { listing ->
                val level = activityLevels[listing.activity.id] ?: programLevels[listing.program.id] ?: NotifyLevel.NONE
                if (level == NotifyLevel.NONE) emptyList()
                else items(listing, level, isNew = listing.activity.id in fresh && listing.program.id in programLevels, zone, today)
            }.filter { item ->
                delivered.none { it.activityId == item.listing.activity.id && it.kind == item.kind && (item.kind == PushKind.NEW_ACTIVITY || it.date == today) }
            }
            if (items.isEmpty()) return@forEach
            val messages = if (items.size > MAX_SEPARATE) listOf(summary(items) to items) else items.map { message(it, zone) to listOf(it) }
            messages.forEach { (message, covered) ->
                val results = group.map { device ->
                    sender.send(device.platform, device.token, message).also { if (it == PushResult.UNREGISTERED) push.deleteToken(device.token) }
                }
                if (PushResult.SENT in results) {
                    val records = covered.map { PushDelivery(userId, it.listing.activity.id, it.kind, today) }
                    push.saveDeliveries(records, now)
                    delivered += records
                    count++
                }
            }
        }
        return count
    }

    private class Item(val listing: ActivityListing, val kind: PushKind, val scheduleTitle: String? = null)

    private fun items(listing: ActivityListing, level: NotifyLevel, isNew: Boolean, zone: ZoneId, today: LocalDate): List<Item> = buildList {
        val activity = listing.activity
        if (isNew) add(Item(listing, PushKind.NEW_ACTIVITY))
        activity.application?.takeIf { it.status != ScheduleStatus.CANCELLED }?.time?.let { time ->
            if (!isNew && startDate(time, zone) == today) add(Item(listing, PushKind.APPLICATION_START))
            when (deadlineDate(time, zone)) {
                today.plusDays(1) -> add(Item(listing, PushKind.DEADLINE_EVE))
                today -> add(Item(listing, PushKind.DEADLINE))
                else -> Unit
            }
        }
        if (level == NotifyLevel.ALL) {
            (listOf(activity.activitySchedule) + activity.schedules)
                .firstOrNull { it.status != ScheduleStatus.CANCELLED && it.time?.let { t -> pointDate(t, zone) } == today }
                ?.let { add(Item(listing, PushKind.SCHEDULE, it.title)) }
        }
    }

    private fun message(item: Item, zone: ZoneId): PushMessage {
        val activity = item.listing.activity
        val program = item.listing.program.name
        val deadline = activity.application?.time?.let { deadlineDate(it, zone) }?.let { "${it.monthValue}월 ${it.dayOfMonth}일 마감" }
        val (title, body) = when (item.kind) {
            PushKind.NEW_ACTIVITY -> "$program 새 모집이 열렸어요" to listOfNotNull(activity.name, deadline).joinToString(" · ")
            PushKind.APPLICATION_START -> "${activity.name} 모집이 시작됐어요" to listOfNotNull(program, deadline).joinToString(" · ")
            PushKind.DEADLINE_EVE -> "${activity.name} 내일 마감" to program
            PushKind.DEADLINE -> "${activity.name} 오늘 마감" to program
            PushKind.SCHEDULE -> "${activity.name} 오늘 ${item.scheduleTitle}" to program
        }
        return PushMessage(title, body, activity.id, "${properties.webBaseUrl}/activities/${activity.id}")
    }

    // 거리가 3개를 넘으면 하나로: 누르면 앱의 즐겨찾기 캘린더
    private fun summary(items: List<Item>): PushMessage {
        val names = items.map { it.listing.activity.name }.distinct()
        val body = names.take(2).joinToString(" · ") + if (names.size > 2) " 외 ${names.size - 2}개" else ""
        return PushMessage("오늘 일정 ${items.size}개", body, null, FAVORITES_CALENDAR)
    }

    // 종일 일정은 적힌 날짜 그대로, 시각 일정은 기기 시간대의 날짜
    private fun startDate(time: ScheduleTime, zone: ZoneId): LocalDate? = when (time) {
        is ScheduleTime.Timed -> time.start?.withZoneSameInstant(zone)?.toLocalDate()
        is ScheduleTime.AllDay -> time.start
    }

    // 마감 날짜: 0시 마감은 그 전날 끝으로 본다
    private fun deadlineDate(time: ScheduleTime, zone: ZoneId): LocalDate? = when (time) {
        is ScheduleTime.Timed -> time.end?.minusSeconds(1)?.withZoneSameInstant(zone)?.toLocalDate()
        is ScheduleTime.AllDay -> time.endExclusive.minusDays(1)
    }

    // 발표·면접·행사·활동 시작: 시작이 없으면(시점 일정) 끝
    private fun pointDate(time: ScheduleTime, zone: ZoneId): LocalDate? = when (time) {
        is ScheduleTime.Timed -> (time.start ?: time.end)?.withZoneSameInstant(zone)?.toLocalDate()
        is ScheduleTime.AllDay -> time.start
    }

    private fun zoneOf(device: PushDevice): ZoneId = try {
        ZoneId.of(device.timeZone)
    } catch (_: DateTimeException) {
        SEOUL
    }

    companion object {
        const val SEND_HOUR = 8
        const val MAX_SEPARATE = 3
        const val FAVORITES_CALENDAR = "dearby://favorites?view=calendar"
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
