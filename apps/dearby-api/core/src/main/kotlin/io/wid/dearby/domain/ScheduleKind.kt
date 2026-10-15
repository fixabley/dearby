package io.wid.dearby.domain

// CATEGORIES 값. occupiesTime이 false면 TRANSP:TRANSPARENT
enum class ScheduleKind(val occupiesTime: Boolean) {
    DEADLINE(false), // 서류·신청 마감
    ANNOUNCEMENT(false), // 결과 발표
    INTERVIEW(true), // 면접
    EVENT(true), // 행사·교육·본선
}
