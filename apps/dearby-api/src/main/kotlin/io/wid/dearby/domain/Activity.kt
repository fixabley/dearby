package io.wid.dearby.domain

import java.time.Duration
import java.time.OffsetDateTime

data class Activity(
    val id: Id,
    val programId: Id,
    val name: String,
    val description: String, // ICS 내보낼 때 각 일정의 DESCRIPTION. 카탈로그 원본 summary · 사용: iOS·Android·웹
    val contacts: List<ActivityContact>,
    // 아래는 카탈로그 원본 필드. 주석의 "사용"은 그 값을 읽는 앱이다. 쓰지 않는 필드는 확인 후 지운다
    val participationType: ParticipationType, // 사용: iOS·Android·웹
    val recruitmentStatus: RecruitmentStatus, // 사용: iOS·Android·웹
    val location: String?, // 주소 문구(좌표 없음) · 사용: iOS·Android·웹
    val cost: String?, // 사용: iOS·Android·웹
    val audience: String?, // 사용: iOS·Android·웹
    val roles: List<String>, // 모집 역할 문구 · 사용: iOS·Android·웹
    val officialUrl: String, // 공식 안내 · 사용: iOS·Android·웹
    val applicationUrl: String?, // 신청 링크 · 사용: iOS·Android·웹
    val sourceCheckedAt: OffsetDateTime?, // 공식 안내를 마지막으로 확인한 시각 · 사용: iOS·Android·웹
    val validUntil: OffsetDateTime?, // 확인 내용이 유효한 끝 시각 · 사용: iOS·Android·웹
    val freshness: Freshness, // 사용: iOS·Android·웹
    val sourceNote: String, // 공개 안내 문구 · 사용: iOS·Android·웹
    val application: Schedule?, // 신청 기간(kind = DEADLINE). 카탈로그 원본 recruitmentStartAt·EndAt → Timed(start, end), 하나만 있으면 그쪽만. null: 상시 모집 등 신청 기간 없음
    val activitySchedule: Schedule, // 활동 기간(kind = EVENT, 종일 일정). 활동 장소의 기준
    val schedules: List<Schedule>, // 면접·발표 등 개별 일정
    val timestamps: Timestamps,
) {
    init {
        require(activitySchedule.time == null || activitySchedule.time is ScheduleTime.AllDay) { "활동 기간은 종일 일정이어야 합니다" }
    }

    // 사용자가 캘린더에 추가할 일정을 고르는 후보
    val calendarCandidates: List<Schedule> get() = listOfNotNull(application, activitySchedule) + schedules

    // 확인 내용은 확인 시각부터 validUntil 전까지, 최대 VERIFICATION_LIFETIME 동안만 VERIFIED로 본다.
    // 모집 기간이 있으면 기간이 상태를 정하고(시작 포함·끝 제외), 운영자가 고른 상태는 기간이 없을 때만 쓴다. CLOSED는 항상 우선(조기 마감)
    fun recruitmentAt(now: OffsetDateTime): Recruitment {
        val checked = sourceCheckedAt
        val until = validUntil
        val verified = freshness == Freshness.VERIFIED && checked != null && until != null &&
            !checked.isAfter(now) && now.isBefore(until) && !until.isAfter(checked.plus(VERIFICATION_LIFETIME))
        val currentFreshness = when {
            verified -> Freshness.VERIFIED
            freshness == Freshness.UNAVAILABLE -> Freshness.UNAVAILABLE
            else -> Freshness.STALE
        }
        val window = application?.time as? ScheduleTime.Timed
        val start = window?.start?.toOffsetDateTime()
        val end = window?.end?.toOffsetDateTime()
        val status = when {
            recruitmentStatus == RecruitmentStatus.CLOSED || (end != null && !now.isBefore(end)) -> RecruitmentStatus.CLOSED
            !verified -> RecruitmentStatus.UNKNOWN
            start != null && now.isBefore(start) -> RecruitmentStatus.SCHEDULED
            start != null || end != null -> RecruitmentStatus.OPEN
            else -> recruitmentStatus
        }
        return Recruitment(status, currentFreshness, verified && status == RecruitmentStatus.OPEN)
    }

    companion object {
        val VERIFICATION_LIFETIME: Duration = Duration.ofHours(24)
    }
}
