package io.wid.dearby.admin.adaptor.web

import tools.jackson.databind.JsonNode

// 보낸 필드만 바꾼다. 영문 값은 빈 문자열이면 지운다.
// eligibility(native-v1.md "대상자 조건")·activitySchedule·application(admin-v1.md "일정 직접 지정"):
// 보내지 않으면 그대로, JSON null이면 지우고, 객체면 바꾼다(JsonNode라 null과 생략을 구분한다)
data class ActivityTextsRequest(
    val name: String? = null,
    val nameEn: String? = null,
    val description: String? = null,
    val descriptionEn: String? = null,
    val eligibility: JsonNode? = null,
    val activitySchedule: JsonNode? = null,
    val application: JsonNode? = null,
)
