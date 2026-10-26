package io.wid.dearby.domain

import java.time.OffsetDateTime
import java.time.ZoneId

// 활동 상태(native-v1.md "활동 상태", 2026-10-26 사용자 결정). 서버가 서울 시각으로 계산한다. SQL 판정은 OpenActivityConditions.status와 같은 순서다:
// 활동 기간이 끝났으면 ENDED → 모집 기간이 지금을 포함하면 RECRUITING → 모집 시작 전이면 RECRUITING_UPCOMING → 활동 기간 안이면 IN_PROGRESS
// → 모집이 끝났고 활동 기간이 있으면 RECRUITMENT_CLOSED, 활동 기간이 없으면 ENDED → 모집 일정이 없으면 NO_RECRUITMENT
enum class ActivityStatus {
    RECRUITING_UPCOMING, RECRUITING, RECRUITMENT_CLOSED, NO_RECRUITMENT, IN_PROGRESS, ENDED;

    companion object {
        private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")

        // 쉼표 목록(대소문자 무시). 생략·빈 값은 null(옛 동작), 모르는 값은 400
        fun parseList(value: String?): Set<ActivityStatus>? = value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() }
            ?.map { code -> entries.firstOrNull { it.name.equals(code, ignoreCase = true) } ?: throw InvalidInputException("status를 알 수 없습니다: $code") }
            ?.toSet()

        fun of(application: ScheduleTime?, activity: ScheduleTime?, now: OffsetDateTime): ActivityStatus {
            val today = now.atZoneSameInstant(SEOUL).toLocalDate()
            val activityStart = when (activity) {
                is ScheduleTime.Timed -> activity.start?.toOffsetDateTime()
                is ScheduleTime.AllDay -> activity.start.atStartOfDay(SEOUL).toOffsetDateTime()
                null -> null
            }
            // 끝을 모르면 활동 시작(OpenActivityConditions.activityEnd와 같다)
            val activityEnd = when (activity) {
                is ScheduleTime.Timed -> activity.end?.toOffsetDateTime()
                is ScheduleTime.AllDay -> activity.endExclusive.atStartOfDay(SEOUL).toOffsetDateTime()
                null -> null
            } ?: activityStart
            if (activityEnd != null && !activityEnd.isAfter(now)) return ENDED
            when (application) {
                is ScheduleTime.Timed -> {
                    val start = application.start?.toOffsetDateTime()
                    val end = application.end?.toOffsetDateTime()
                    if ((start == null || !start.isAfter(now)) && (end == null || end.isAfter(now))) return RECRUITING
                    if (start != null && start.isAfter(now)) return RECRUITING_UPCOMING
                }
                is ScheduleTime.AllDay -> {
                    if (!application.start.isAfter(today) && application.endExclusive.isAfter(today)) return RECRUITING
                    if (application.start.isAfter(today)) return RECRUITING_UPCOMING
                }
                null -> Unit
            }
            if (activityStart != null && !activityStart.isAfter(now)) return IN_PROGRESS
            return when {
                application == null -> NO_RECRUITMENT
                activityEnd == null -> ENDED
                else -> RECRUITMENT_CLOSED
            }
        }
    }
}
