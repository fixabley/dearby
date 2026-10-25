package io.wid.dearby.batch.push

import io.wid.dearby.application.activity.ActivityListing
import io.wid.dearby.application.activity.ActivityService
import io.wid.dearby.application.favorite.FavoriteRepository
import io.wid.dearby.application.notification.NotificationRepository
import io.wid.dearby.application.push.PushRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ScheduleKind
import io.wid.dearby.domain.ScheduleStatus
import io.wid.dearby.domain.ScheduleTime
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.favorite.FavoriteTarget
import io.wid.dearby.domain.favorite.NotifyLevel
import io.wid.dearby.domain.notification.NotificationDraft
import io.wid.dearby.domain.notification.NotificationKind
import io.wid.dearby.domain.notification.UserNotification
import io.wid.dearby.domain.push.PushDevice
import io.wid.dearby.domain.push.PushPlatform
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.DateTimeException
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// 즐겨찾기 알림(native-v1.md "프로그램·활동별 알림 수준", "알림 목록"). 매시 돈다.
// 1) 알림 목록: 사용자 시간대(가장 최근 기기, 없으면 서울)와 각 기기 시간대가 아침 8시면 그 날짜의 알림 거리를 user_notifications에 쌓는다.
//    푸시 권한·전체 스위치와 상관없고, 알림 수준 없음만 빠진다. 거리: 신규(새로 게시되어 열린 활동, 한 번만) · 신청일(모집 시작 당일·마감 하루 전·마감 당일) · 전체면 그 밖의 일정 당일.
//    활동 단위 수준이 프로그램 수준보다 앞선다. 90일 지난 알림은 지운다.
// 2) 푸시: 그 시간대가 8시인 기기마다 그 날짜의 알림 행(+ 아직 안 보낸 최근 신규)을 보낸다. (알림, 기기)마다 한 번(push_deliveries).
//    거리가 3개를 넘으면 "오늘 일정 n개" 하나로 묶는다(기기마다). 전체 스위치가 꺼지면 보내지 않는다. 토큰은 로그에 남기지 않는다
@Service
class FavoritePushService(
    private val push: PushRepository,
    private val favorites: FavoriteRepository,
    private val notifications: NotificationRepository,
    private val activities: ActivityService,
    private val sender: PushSender,
    private val properties: PushProperties,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    data class Report(val devices: Int, val notifications: Int)

    // anyHour: --send-push-now(수동 실행)는 시각과 상관없이 지금 쌓고 보낸다. 같은 날 다시 쌓거나 보내지 않는다
    fun send(now: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC), anyHour: Boolean = false): Report {
        fun due(zone: ZoneId) = anyHour || now.atZoneSameInstant(zone).hour == SEND_HOUR
        val users = favorites.userIds().toSet()
        val devicesByUser = push.devices().groupBy { it.userId }
        val disabled = push.disabledUsers(users)
        val fresh = favorites.newRecruitments(users, now)
        val problems = mutableMapOf<PushPlatform, String?>()
        var dueDevices = 0
        var sent = 0
        users.forEach { userId ->
            runCatching {
                val devices = devicesByUser[userId].orEmpty()
                val zones = (listOf(devices.firstOrNull()?.let(::zoneOf) ?: SEOUL) + devices.map(::zoneOf)).distinct().filter(::due)
                if (zones.isEmpty()) return@forEach
                val inputs = load(userId, fresh[userId].orEmpty().values.flatten().toSet())
                zones.forEach { zone -> notifications.saveAll(userId, drafts(inputs, zone, today(now, zone)), now) }
                if (userId in disabled) return@forEach
                devices.filter { due(zoneOf(it)) }.forEach { device ->
                    dueDevices++
                    val problem = problems.getOrPut(device.platform) {
                        sender.problem(device.platform)?.also { log.info("Push to {} skipped: {}", device.platform, it) }
                    }
                    if (problem == null) sent += sendTo(device, inputs, now)
                }
            }.onFailure { log.warn("Notifications failed for a user", it) }
        }
        val removed = notifications.deleteBefore(now.minusDays(KEEP_DAYS))
        log.info("Notifications: {} users, {} devices due, {} pushes sent, {} old removed", users.size, dueDevices, sent, removed)
        return Report(dueDevices, sent)
    }

    // 사용자의 즐겨찾기 수준과 대상 활동(즐겨찾기 활동 + 즐겨찾기 프로그램의 게시 활동)
    private class Inputs(val listings: Map<Id, ActivityListing>, val activityLevels: Map<Id, NotifyLevel>, val programLevels: Map<Id, NotifyLevel>, val fresh: Set<Id>)

    private fun load(userId: UserId, fresh: Set<Id>): Inputs {
        val all = favorites.findAll(userId)
        val programLevels = all.filter { it.target == FavoriteTarget.PROGRAM }.associate { it.targetId to it.notifyLevel }
        val activityLevels = all.filter { it.target == FavoriteTarget.ACTIVITY }.associate { it.targetId to it.notifyLevel }
        val listings = (activities.publishedListings(ids = activityLevels.keys) + activities.publishedListings(programIds = programLevels.keys))
            .associateBy { it.activity.id }
        return Inputs(listings, activityLevels, programLevels, fresh)
    }

    private fun drafts(inputs: Inputs, zone: ZoneId, today: LocalDate): List<NotificationDraft> = inputs.listings.values.flatMap { listing ->
        val level = inputs.activityLevels[listing.activity.id] ?: inputs.programLevels[listing.program.id] ?: NotifyLevel.NONE
        if (level == NotifyLevel.NONE) return@flatMap emptyList()
        val isNew = listing.activity.id in inputs.fresh && listing.program.id in inputs.programLevels
        kinds(listing, level, isNew, zone, today).map { NotificationDraft(listing.activity.id, listing.program.id, it, today) }
    }

    private fun kinds(listing: ActivityListing, level: NotifyLevel, isNew: Boolean, zone: ZoneId, today: LocalDate): Set<NotificationKind> = buildSet {
        val activity = listing.activity
        if (isNew) add(NotificationKind.NEW)
        activity.application?.takeIf { it.status != ScheduleStatus.CANCELLED }?.time?.let { time ->
            if (!isNew && startDate(time, zone) == today) add(NotificationKind.APPLICATION_START)
            when (deadlineDate(time, zone)) {
                today.plusDays(1) -> add(NotificationKind.DEADLINE_EVE)
                today -> add(NotificationKind.DEADLINE)
                else -> Unit
            }
        }
        if (level == NotifyLevel.ALL) {
            fun onDay(s: io.wid.dearby.domain.Schedule) = s.status != ScheduleStatus.CANCELLED && s.time?.let { pointDate(it, zone) } == today
            if (onDay(activity.activitySchedule)) add(NotificationKind.ACTIVITY_START)
            activity.schedules.filter(::onDay).forEach {
                add(when (it.kind) {
                    ScheduleKind.ANNOUNCEMENT -> NotificationKind.ANNOUNCEMENT
                    ScheduleKind.INTERVIEW -> NotificationKind.INTERVIEW
                    ScheduleKind.EVENT -> NotificationKind.EVENT
                    ScheduleKind.DEADLINE -> NotificationKind.DEADLINE
                })
            }
        }
    }

    // 그 기기 날짜의 알림 + 하루 안에 만든 신규 중 이 기기로 아직 안 보낸 것
    private fun sendTo(device: PushDevice, inputs: Inputs, now: OffsetDateTime): Int {
        val zone = zoneOf(device)
        val rows = notifications.forPush(device.userId, today(now, zone), newSince = now.minusDays(1)).filter { it.activityId in inputs.listings }
        val done = notifications.delivered(rows.map { it.id }, device.token)
        val pending = rows.filter { it.id !in done }
        if (pending.isEmpty()) return 0
        val messages = if (pending.size > MAX_SEPARATE) listOf(summary(pending) to pending) else pending.map { message(it, inputs, zone) to listOf(it) }
        var count = 0
        for ((message, covered) in messages) {
            when (sender.send(device.platform, device.token, message)) {
                PushResult.SENT -> {
                    notifications.saveDeliveries(covered.map { it.id }, device.token, now)
                    count++
                }
                PushResult.UNREGISTERED -> {
                    push.deleteToken(device.token)
                    break
                }
                PushResult.FAILED -> Unit
            }
        }
        return count
    }

    private fun message(row: UserNotification, inputs: Inputs, zone: ZoneId): PushMessage {
        val listing = inputs.listings.getValue(row.activityId)
        val activity = listing.activity
        val program = listing.program.name
        val deadline = activity.application?.time?.let { deadlineDate(it, zone) }?.let { "${it.monthValue}월 ${it.dayOfMonth}일 마감" }
        val (title, body) = when (row.kind) {
            NotificationKind.NEW -> "$program 새 모집이 열렸어요" to listOfNotNull(activity.name, deadline).joinToString(" · ")
            NotificationKind.APPLICATION_START -> "${activity.name} 모집이 시작됐어요" to listOfNotNull(program, deadline).joinToString(" · ")
            NotificationKind.DEADLINE_EVE -> "${activity.name} 내일 마감" to program
            NotificationKind.DEADLINE -> "${activity.name} 오늘 마감" to program
            NotificationKind.ANNOUNCEMENT -> "${activity.name} 오늘 발표" to program
            NotificationKind.INTERVIEW -> "${activity.name} 오늘 면접" to program
            NotificationKind.EVENT -> "${activity.name} 오늘 행사" to program
            NotificationKind.ACTIVITY_START -> "${activity.name} 오늘 활동 시작" to program
        }
        return PushMessage(title, body, activity.id, "${properties.webBaseUrl}/activities/${activity.id}", row.id)
    }

    // 거리가 3개를 넘으면 하나로: 누르면 앱의 즐겨찾기 캘린더(알림 하나를 가리키지 않는다)
    private fun summary(rows: List<UserNotification>): PushMessage {
        val names = rows.map { it.activityName }.distinct()
        val body = names.take(2).joinToString(" · ") + if (names.size > 2) " 외 ${names.size - 2}개" else ""
        return PushMessage("오늘 일정 ${rows.size}개", body, null, FAVORITES_CALENDAR, null)
    }

    private fun today(now: OffsetDateTime, zone: ZoneId): LocalDate = now.atZoneSameInstant(zone).toLocalDate()

    // 종일 일정은 적힌 날짜 그대로, 시각 일정은 그 시간대의 날짜
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
        const val KEEP_DAYS = 90L
        const val FAVORITES_CALENDAR = "dearby://favorites?view=calendar"
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
