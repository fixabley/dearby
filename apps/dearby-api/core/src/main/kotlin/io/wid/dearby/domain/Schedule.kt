package io.wid.dearby.domain

// iCalendar(RFC 5545·RFC 7986) VEVENT 대응
data class Schedule(
    val id: Id, // UID: 내보낼 때 "schedule-{id}@dearby"처럼 전역 고유 문자열로 변환
    val title: String, // SUMMARY
    val kind: ScheduleKind, // CATEGORIES, TRANSP 판단 근거
    val status: ScheduleStatus, // STATUS
    val time: ScheduleTime?, // DTSTART/DTEND. null: 공식 일정이 날짜를 확정하지 않음 → dateLabel만 표시, ICS로 내보내지 않음
    val dateLabel: String, // ICS 대응 없음. 공식 공지의 일정 문구 원문
    val address: Address?, // LOCATION(addressLine1 + addressLine2), GEO(lat;lng). null이면 Activity.activitySchedule.address 사용
    val conferenceUrl: String?, // CONFERENCE(RFC 7986): 온라인 진행 링크
    val sequence: Int, // SEQUENCE: 내용이 바뀔 때마다 1씩 증가. 구독 캘린더가 변경을 인식하는 기준
    val timestamps: Timestamps, // CREATED, LAST-MODIFIED. DTSTAMP는 내보낸 시각
) {
    // TRANSP: 마감·발표 같은 시점 일정과 종일 일정은 TRANSPARENT(겹침 계산 제외)
    val occupiesTime: Boolean get() = kind.occupiesTime && time !is ScheduleTime.AllDay
}
