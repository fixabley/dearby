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
)
