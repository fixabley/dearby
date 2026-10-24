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
    val tags: List<String> = emptyList(), // 성격 사전에서 고른 태그 이름(native-v1.md "태그"). 사전 밖 이름은 저장할 때 버린다
    val fieldTags: List<ExtractedFieldTag> = emptyList(), // 분야 사전에서 고른 태그와 연관도(native-v1.md "분야·성격 태그와 아이콘")
    val activityRecurrence: ExtractedRecurrence? = null, // 활동 기간의 규칙적인 시간표(예: 평일 09:00~17:00). startDate부터
    val place: ExtractedPlace? = null, // 한 곳을 가리키는 장소(native-v1.md "수집기 장소"). 지역만·여러 곳이면 null
)

// 분야 태그 이름과 그 활동과의 연관도(0~100)
data class ExtractedFieldTag(val name: String, val relevance: Int? = null)

// 장소 이름(건물·홀·캠퍼스)·본문의 주소·온라인 여부와 근거 구절(본문에서 그대로 복사, 이름이 들어 있어야 한다)
data class ExtractedPlace(val name: String?, val address: String?, val online: Boolean = false, val evidenceQuote: String?)

// 반복 규칙(RRULE 본문)과 매 회차 시각(HH:mm, 서울)
data class ExtractedRecurrence(val rule: String, val startTime: String, val endTime: String)
