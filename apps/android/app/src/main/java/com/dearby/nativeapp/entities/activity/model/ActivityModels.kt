package com.dearby.nativeapp.entities.activity.model

import java.net.URI
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.OffsetDateTime

/** A timed window with both ends (ISO 8601 with offset), as the calendar comparison needs it; built by [StageSchedule.windows]. */
data class ScheduleModel(val title: String, val startAt: String, val endAt: String, val timeZone: String = "Asia/Seoul")

/**
 * One choice a schedule offers on the progress page (contract "내 활동 서버 저장·일정 상태", 2026-10-22): the admin's [name] or the
 * default 대기·통과·탈락. [id] is null for a default status (the server then takes [kind]). [kind] is WAITING, PASSED, FAILED or OTHER.
 */
data class ScheduleStatus(val id: String?, val name: String, val kind: String) {
    companion object {
        const val WAITING = "WAITING"; const val PASSED = "PASSED"; const val FAILED = "FAILED"
        /** The server fills these in when the admin set none; kept for an API that sent no `statuses`. */
        val DEFAULTS = listOf(ScheduleStatus(null, "대기", WAITING), ScheduleStatus(null, "통과", PASSED), ScheduleStatus(null, "탈락", FAILED))
    }
}

/**
 * One schedule as `GET /v1/activities` sends it (contract "활동 조회"): timed ([startAt] and/or [endAt]), all-day
 * ([startDate]/[endDateExclusive]) or undated ([dateLabel] only). [kind] is DEADLINE, ANNOUNCEMENT, INTERVIEW or EVENT.
 * [id] is the schedule id the progress status is saved under; [statuses] its choices; [overlapStart]/[overlapEnd] the admin's
 * Seoul `HH:mm` overlap-check window for a date-only schedule (contract "겹침 확인 시간", 2026-10-22); [recurrence] the RRULE body
 * (contract "반복 일정과 겹침 결과 묶음"), null for a one-off schedule.
 */
data class StageSchedule(
    val title: String, val kind: String, val startAt: String?, val endAt: String?, val timeZone: String,
    val startDate: String?, val endDateExclusive: String?, val dateLabel: String,
    val id: String? = null, val statuses: List<ScheduleStatus> = emptyList(), val overlapStart: String? = null, val overlapEnd: String? = null,
    val recurrence: String? = null,
    /** The block has a 트랙·세션 시간표 (contract "트랙·세션 시간표": `hasTimetable`), opened with `GET …/schedules/:id/timetable`. */
    val hasTimetable: Boolean = false,
) {
    /**
     * The windows the calendar comparison uses (contract "겹침 확인 시간"): a timed schedule with both ends is its own window; a
     * date-only schedule with an overlap-check time is that Seoul time on every day of `[startDate, endDateExclusive)`; anything
     * else is not compared. With a readable [recurrence] (contract "반복 일정과 겹침 결과 묶음") the rule's days replace those:
     * the timed window, or the overlap-check time from `startDate`, repeats on each, at most 400. Windows are written as UTC instants.
     */
    fun windows(): List<ScheduleModel> {
        val start = instant(startAt); val end = instant(endAt)
        val rule = Recurrence.parse(recurrence)
        if (start != null && end != null) {
            if (rule == null) return listOf(ScheduleModel(title, start.toString(), end.toString(), timeZone))
            val zone = runCatching { ZoneId.of(timeZone) }.getOrDefault(SEOUL_ZONE)
            val first = start.atZone(zone).toLocalDateTime()
            val length = Duration.between(start, end)
            return rule.days(first, zone).map { day ->
                val begin = day.atTime(first.toLocalTime()).atZone(zone).toInstant()
                ScheduleModel(title, begin.toString(), begin.plus(length).toString(), zone.id)
            }
        }
        if (start != null || end != null) return emptyList()
        val from = runCatching { LocalTime.parse(overlapStart) }.getOrNull() ?: return emptyList()
        val to = runCatching { LocalTime.parse(overlapEnd) }.getOrNull()?.takeIf { it > from } ?: return emptyList()
        val first = startDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return emptyList()
        val last = endDateExclusive?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.minusDays(1)?.takeIf { it >= first } ?: first
        val days = rule?.days(first.atTime(from), SEOUL_ZONE) ?: generateSequence(first) { day -> day.plusDays(1).takeIf { it <= last } }.toList()
        return days.map { day ->
            ScheduleModel(title, day.atTime(from).atZone(SEOUL_ZONE).toInstant().toString(), day.atTime(to).atZone(SEOUL_ZONE).toInstant().toString(), SEOUL_ZONE.id)
        }
    }
}
private val SEOUL_ZONE: ZoneId = ZoneId.of("Asia/Seoul")

/**
 * Recruitment period from `application`: timed ([startAt]/[endAt]) or all-day ([startDate]/[endDateExclusive], Seoul days);
 * either end may be unknown. [id] and [statuses] are the APPLICATION schedule's, for the 지원 stage's status.
 */
data class ApplicationWindow(val startAt: String?, val endAt: String?, val startDate: String? = null, val endDateExclusive: String? = null,
    val id: String? = null, val statuses: List<ScheduleStatus> = emptyList())

/** A structured venue (contract "활동 장소"): all four fields come together or the activity has no place. */
data class Place(val name: String, val address: String, val lat: Double, val lng: Double)

/** One activity from `GET /v1/activities` (contract "활동 조회"). [id] is the API's number as text. */
data class ActivityModel(
    val id: String, val name: String, val description: String, val organization: String, val program: String,
    val officialUrl: String, val imageUrl: String?, val application: ApplicationWindow?,
    val dateLabel: String, val address: String?,
    /** Every non-slot schedule in API order, for the progress track (contract "내 활동 진행 트랙"). */
    val stageSchedules: List<StageSchedule>,
    /** The ACTIVITY slot (activity period), the last stage of the track; null only when the API omitted it. */
    val activitySchedule: StageSchedule?,
    /** 대상자 조건; null when the activity has none or the JSON could not be read. */
    val eligibility: EligibilityGroup? = null,
    /** 활동 종류; null = 미분류 (contract "활동 종류(category)"). */
    val category: ActivityCategory? = null,
    /** 분야, at most 2 in the server's relevance order (contract "분야·성격 태그와 아이콘"), for the expanded row; empty when the API sent none. */
    val fieldTags: List<Tag> = emptyList(),
    /** 얻을 수 있는 것 (성격), dictionary order; an older API's `tags` when `traitTags` is missing. */
    val traitTags: List<Tag> = emptyList(),
    /** Every 분야 in the same order, for the detail's `분야` group. */
    val fieldTagsAll: List<Tag> = emptyList(),
    /** The collected venue phrase (`location`), already the place name when a [place] is set (contract "활동 장소"). */
    val location: String? = null,
    /** The curated venue with coordinates, or null (contract "활동 장소"). */
    val place: Place? = null,
    /** The operator's application link (contract "신청 확인 조건 완화·공개 applicationUrl"), or null; https only when used. */
    val applicationUrl: String? = null,
    /** 한 줄 소개 (contract "맞춤 대기열": `hook`), null when the API sent none. */
    val hook: String? = null,
    /** 핵심 세 줄 (`highlights`); empty when the API sent none. */
    val highlights: List<Highlight> = emptyList(),
) {
    /** The venue name to show: the curated place, else the collected phrase, else the schedule address. */
    val venue: String? get() = place?.name ?: location ?: address
    /**
     * The calendar-comparison windows of the activity period and its schedules (not the recruitment period), earliest first
     * (contract "겹침 확인 시간"); empty hides the detail's `겹치는 시간 확인하기`.
     */
    val schedules get() = (listOfNotNull(activitySchedule) + stageSchedules).flatMap { it.windows() }.sortedBy { instant(it.startAt) }
    /**
     * Only `application` against [now] (2026-10-14 rule; 2026-10-19 "열린 활동 판정 일원화" adds date-only windows). A timed
     * window compares instants, an all-day window compares Seoul dates (`startDate ≤ today < endDateExclusive`); a missing
     * bound is open on that side. No application, or a value that cannot be read, is unknown.
     */
    fun statusLabel(now: Instant): String {
        val window = application ?: return UNKNOWN
        if (window.startAt != null || window.endAt != null) {
            val start = window.startAt?.let { instant(it) ?: return UNKNOWN }
            val end = window.endAt?.let { instant(it) ?: return UNKNOWN }
            return when {
                start != null && now < start -> "모집 예정"
                end != null && now >= end -> "모집 마감"
                else -> OPEN
            }
        }
        if (window.startDate == null && window.endDateExclusive == null) return UNKNOWN
        val today = now.atZone(SEOUL).toLocalDate()
        val start = window.startDate?.let { date(it) ?: return UNKNOWN }
        val endExclusive = window.endDateExclusive?.let { date(it) ?: return UNKNOWN }
        return when {
            start != null && today < start -> "모집 예정"
            endExclusive != null && today >= endExclusive -> "모집 마감"
            else -> OPEN
        }
    }
    /**
     * Applying is over (2026-10-19 밤 "신청 확인 조건 완화", same rule as iOS): the recruitment deadline passed, or, with no window to
     * judge by, the activity itself ended (its end, else its start, is behind [now]). Open, upcoming and unknown-but-not-ended still ask.
     */
    fun isClosed(now: Instant) = when (statusLabel(now)) {
        CLOSED -> true
        UNKNOWN -> (activityEnd ?: activityStart)?.let { it < now } == true
        else -> false
    }
    /** The application link for the detail CTA — [applicationUrl], else the official page — unless recruitment is closed. */
    fun applyUrl(now: Instant) = if (isClosed(now)) null else safeHttpsUrl(applicationUrl) ?: safeHttpsUrl(officialUrl)
    val recruitmentEnd get() = instant(application?.endAt)
    /**
     * Ordering key for discovery groups (contract "활동 종류", 2026-10-19): the recruitment deadline, or the activity start
     * (ACTIVITY slot `startAt`, else `startDate` at Seoul midnight) when the activity has no deadline; null sorts last.
     */
    val sortKey: Instant? get() = recruitmentEnd ?: activityStart
    /**
     * When the activity becomes relevant next (contract "모집 중인 프로그램만 보기", 2026-10-19 보완): the recruitment start
     * (`startAt`, else `startDate` at Seoul midnight), or the activity start when there is no recruitment window; null when unknown.
     */
    val upcomingKey: Instant? get() = application?.let { window ->
        instant(window.startAt) ?: window.startDate?.let { runCatching { LocalDate.parse(it).atStartOfDay(SEOUL).toInstant() }.getOrNull() }
    } ?: activityStart
    /** The ACTIVITY slot's start: `startAt`, else `startDate` at Seoul midnight; null when the period is undated. */
    val activityStart: Instant? get() = instant(activitySchedule?.startAt)
        ?: activitySchedule?.startDate?.let { runCatching { LocalDate.parse(it).atStartOfDay(SEOUL).toInstant() }.getOrNull() }
    /** The ACTIVITY slot's end: `endAt`, else `endDateExclusive` at Seoul midnight; null when the period is undated. */
    val activityEnd: Instant? get() = instant(activitySchedule?.endAt)
        ?: activitySchedule?.endDateExclusive?.let { runCatching { LocalDate.parse(it).atStartOfDay(SEOUL).toInstant() }.getOrNull() }

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
        fun date(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()
    }
}

/** One page of `GET /v1/activities`; a null [nextCursor] is the last page. */
data class ActivityPage(val items: List<ActivityModel>, val nextCursor: String?)

/**
 * One program with open activities from `GET /v1/discovery/programs` (contract "발견 프로그램 묶음"). [id] is the API's number as
 * text; [nearestRecruitmentEndAt] is the earliest open deadline (ISO 8601) or null; [matchedActivityIds] are the open
 * activities whose name matched the search; [latestActivityAt] is the most recent activity start of its published
 * activities (contract "마감 숨기기"), and [openActivityCount] may be 0 when the program only has upcoming or past activities.
 * [upcomingActivityCount] counts 모집 예정 activities and [nearestRecruitmentStartAt] is their earliest recruitment (or activity)
 * start (2026-10-19 밤 "마감 숨기기" 정정: the switch hides only closed activities).
 */
data class ProgramSummary(
    val id: String, val name: String, val nameEn: String?, val organization: String, val openActivityCount: Int,
    val nearestRecruitmentEndAt: String?, val matchedActivityIds: List<String>, val latestActivityAt: String? = null,
    val upcomingActivityCount: Int = 0, val nearestRecruitmentStartAt: String? = null,
    /** [nearestRecruitmentEndAt] is an activity start (an open activity without a recruitment deadline), not a deadline. */
    val nearestIsActivityStart: Boolean = false,
    /** 분야, at most 2 in the server's relevance order (contract "분야·성격 태그와 아이콘"); empty for an older API. */
    val fieldTags: List<Tag> = emptyList(),
    /** 얻을 수 있는 것 (성격), dictionary order; an older API's `tags` when `traitTags` is missing. */
    val traitTags: List<Tag> = emptyList(),
    /** The program logo as an absolute URL (contract "프로그램 로고": `/v1/programs/{id}/logo?v=` on the API origin), or null when it has none. */
    val logoUrl: String? = null,
    /** The 4:5 cover (contract "맞춤 대기열": `/v1/programs/{id}/cover?v=`) as an absolute URL, or null. */
    val coverUrl: String? = null,
)

/** One icon-and-text line: an activity's 핵심 세 줄 (`cost`·`period`·`team`·`mentor`·`award`·`place`·`talk`·`ticket`) or an ELIGIBILITY scene item. */
data class Highlight(val icon: String, val text: String)

/**
 * One card of 오늘의 맞춤 대기열 (or a 추천 item shown the same way): the activity, its program and the server's short [reasons]. [scenes] are the
 * server's scenes in order (unknown types skipped); null when it sent none, and the app builds them.
 */
data class QueueItem(val activity: ActivityModel, val program: ProgramSummary, val reasons: List<String>, val scenes: List<QueueScene>? = null)

/** A server scene (`item.scenes[]`, by `type`): HOOK·HIGHLIGHTS·ELIGIBILITY·BENEFIT·DEADLINE. */
sealed interface QueueScene {
    data class Hook(val hook: String, val name: String?) : QueueScene
    data class Highlights(val items: List<Highlight>) : QueueScene
    /** [items] `{icon, text}` (`age`·`education`·`schoolYear`·`employment`·`region`·`major`·`nationality`·`experience`·`team`·`other`); [text] when there are none. */
    data class Eligibility(val text: String?, val items: List<Highlight>) : QueueScene
    data class Benefit(val text: String) : QueueScene
    /** [label] the server's heading (`마감까지`·`행사까지`), [dday] days left (negative once passed), null when undated. */
    data class Deadline(val label: String?, val dday: Int?, val deadlineText: String?, val reasons: List<String>) : QueueScene
}

/** One page of `GET /v1/discovery/programs`; [nextCursor] is the next integer offset as text, null on the last page. */
data class ProgramPage(val items: List<ProgramSummary>, val nextCursor: String?)

private const val OPEN = "모집 중"
private const val CLOSED = "모집 마감"
private const val UNKNOWN = "모집 여부 확인 필요"

fun instant(raw: String?): Instant? = raw?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() }

fun safeHttpsUrl(raw: String?): String? = raw?.takeIf { value ->
    runCatching { URI(value) }.getOrNull()?.let { it.scheme == "https" && !it.host.isNullOrBlank() && it.userInfo == null } == true &&
        value.none { it.isISOControl() }
}
