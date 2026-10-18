package io.wid.dearby.batch.application

// 활동에 딸린 일정(선발 단계·테스트·교육·발표·마감). kind는 ScheduleKind 이름이며 모르면 null
data class ExtractedSchedule(val title: String, val kind: String?, val startAt: String?, val endAt: String?, val dateLabel: String)
