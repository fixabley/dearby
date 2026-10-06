package com.dearby.nativeapp.entities.catalog.model

data class ScheduleModel(val title: String, val startAt: String, val endAt: String, val timeZone: String = "Asia/Seoul")
data class ActivityModel(
    val id: String, val title: String, val summary: String, val participation: String,
    val status: String, val date: String, val location: String, val audience: String,
    val url: String, val schedule: ScheduleModel,
)

// Same on iOS: the app starts with one example application.
val demoAppliedActivityIds = setOf("conference")

// Fixed examples: deliberately independent of the device clock and network.
val demoActivities = listOf(
    ActivityModel("conference", "Dearby 개발자 컨퍼런스", "개발자·디자이너·기획자가 함께하는 컨퍼런스", "참가등록형", "모집 중",
        "2026년 10월 24일 13:00~17:00", "서울 코엑스", "개발자 · 디자이너 · 기획자", "https://example.com",
        ScheduleModel("Dearby 개발자 컨퍼런스", "2026-10-24T13:00:00+09:00", "2026-10-24T17:00:00+09:00")),
    ActivityModel("camp", "Dearby 메이커 캠프", "함께 아이디어를 만들고 나누는 메이커 캠프", "선발형 · 신청 후 선정 필요", "모집 중",
        "2026년 11월 7일 10:00~18:00", "서울", "개발 · 디자인 · 기획", "https://example.com",
        ScheduleModel("Dearby 메이커 캠프", "2026-11-07T10:00:00+09:00", "2026-11-07T18:00:00+09:00")),
    ActivityModel("meetup", "Dearby 커뮤니티 밋업", "온라인에서 만나는 Dearby 커뮤니티", "참가등록형", "모집 예정",
        "2026년 11월 21일 14:00~17:00", "온라인", "누구나", "https://example.com",
        ScheduleModel("Dearby 커뮤니티 밋업", "2026-11-21T14:00:00+09:00", "2026-11-21T17:00:00+09:00")),
)
