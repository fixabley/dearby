package io.wid.dearby.batch.application

// 활동에 딸린 일정(선발 단계·테스트·교육·발표·마감). kind는 ScheduleKind 이름이며 모르면 null.
// 시각이 있으면 startAt·endAt, 날짜만 있으면 startDate·endDate(yyyy-MM-dd, 포함), 둘 다 없으면 dateLabel만
data class ExtractedSchedule(
    val title: String,
    val kind: String?,
    val startAt: String?,
    val endAt: String?,
    val dateLabel: String,
    val startDate: String? = null,
    val endDate: String? = null,
    val recurrence: String? = null, // 반복 규칙(RRULE 본문). 시각 일정의 첫 회차(startAt)부터. 검증 단계에서 틀리면 버린다
    val timetable: ExtractedTimetable? = null, // 이 일정 블록의 하위 시간표(native-v1.md "트랙·세션 시간표"). 틀리면 시간표만 버린다
    val phase: String? = null, // 선발 과정(SELECTION)·활동 일정(ACTIVITY). 모르면 null(서버 규칙으로 판정)
)

// 트랙이 없으면(단일 진행) tracks는 빈 배열이고 세션의 track은 null. track은 tracks의 name, null이면 전폭(키노트·쉬는시간·점심)
data class ExtractedTimetable(val tracks: List<ExtractedTrack> = emptyList(), val sessions: List<ExtractedSession> = emptyList())

data class ExtractedTrack(val name: String, val room: String? = null)

data class ExtractedSession(
    val track: String? = null,
    val kind: String? = null, // TALK·KEYNOTE·BREAK·MEAL·OTHER, 모르면 TALK
    val title: String,
    val startAt: String?,
    val endAt: String?,
    val speakers: List<ExtractedSpeaker> = emptyList(),
    val tags: List<String> = emptyList(), // 분야 태그 사전 이름
    val description: String? = null,
)

data class ExtractedSpeaker(val name: String, val affiliation: String? = null)
