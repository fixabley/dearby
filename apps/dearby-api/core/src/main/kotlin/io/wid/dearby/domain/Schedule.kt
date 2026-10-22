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
    val curatedNote: String? = null, // 운영자가 지정하며 남긴 사유(admin-v1.md "사유"). 어드민만 본다
    val curated: Boolean = false, // 운영자가 직접 지정한 일정(admin-v1.md "일정 직접 지정"). 수집기가 덮어쓰지 않는다
    val overlapCheck: OverlapCheck? = null, // 겹침 확인 시간(native-v1.md "겹침 확인 시간"). 없으면 null
    val statuses: List<ProgressStatus> = ProgressStatus.DEFAULTS, // 고를 수 있는 진행 상태. 어드민 지정이 없으면 기본 세 개
) {
    // TRANSP: 마감·발표 같은 시점 일정과 종일 일정은 TRANSPARENT(겹침 계산 제외)
    val occupiesTime: Boolean get() = kind.occupiesTime && time !is ScheduleTime.AllDay
}
