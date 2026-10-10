package io.wid.dearby.domain

data class Activity(
    val id: Id,
    val programId: Id,
    val name: String,
    val description: String, // ICS 내보낼 때 각 일정의 DESCRIPTION
    val contacts: List<ActivityContact>,
    val application: Schedule?, // 신청 기간(kind = DEADLINE). null: 상시 모집 등 신청 기간 없음
    val activitySchedule: Schedule, // 활동 기간(kind = EVENT, 종일 일정). 활동 장소의 기준
    val schedules: List<Schedule>, // 면접·발표 등 개별 일정
    val timestamps: Timestamps,
) {
    init {
        require(activitySchedule.time == null || activitySchedule.time is ScheduleTime.AllDay) { "활동 기간은 종일 일정이어야 합니다" }
    }

    // 사용자가 캘린더에 추가할 일정을 고르는 후보
    val calendarCandidates: List<Schedule> get() = listOfNotNull(application, activitySchedule) + schedules
}
