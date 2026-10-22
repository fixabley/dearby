package io.wid.dearby.batch.application

import tools.jackson.databind.JsonNode

// 모델이 페이지 본문에서 뽑은 활동. 아직 검증 전이라 모든 값을 믿지 않는다(시각은 ISO 8601 문자열 그대로)
data class ExtractedActivity(
    val roundKey: String,
    val name: String,
    val nameEn: String?,
    val description: String,
    val descriptionEn: String?,
    val participationType: String?,
    val applicationUrl: String?,
    val recruitmentStartAt: String?,
    val recruitmentEndAt: String?,
    val recruitmentStartDate: String? = null, // 시각 없이 날짜만 있는 모집 기간(yyyy-MM-dd)
    val recruitmentEndDate: String? = null,
    val dateLabel: String,
    val startDate: String? = null, // 날짜만 있는 활동 기간(yyyy-MM-dd, 포함). 시각이 있는 활동 기간은 받지 않는다(종일 일정만)
    val endDate: String? = null,
    val schedules: List<ExtractedSchedule>,
    val location: String?,
    val cost: String?,
    val audience: String?,
    val eligibility: JsonNode? = null, // 대상자 조건 JSON(native-v1.md "대상자 조건"). 어휘 정리는 검증 단계에서
    val evidenceQuote: String,
    val category: String? = null, // 활동 종류 코드(native-v1.md "활동 종류"). 모르면 null
    val tags: List<String> = emptyList(), // 사전에서 고른 태그 이름(native-v1.md "태그"). 사전 밖 이름은 저장할 때 버린다
    val activityRecurrence: ExtractedRecurrence? = null, // 활동 기간의 규칙적인 시간표(예: 평일 09:00~17:00). startDate부터
)

// 반복 규칙(RRULE 본문)과 매 회차 시각(HH:mm, 서울)
data class ExtractedRecurrence(val rule: String, val startTime: String, val endTime: String)
