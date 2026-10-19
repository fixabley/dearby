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
    val officialUrl: String? = null, // 공식 안내 URL(https). 생략은 유지, 비울 수 없다
    val applicationUrl: String? = null, // 신청 링크(https). 생략은 유지, 빈 문자열은 삭제
    val category: JsonNode? = null, // 활동 종류 코드. 생략은 유지, JSON null은 미분류, 모르는 코드는 400
    val activitySchedule: JsonNode? = null,
    val application: JsonNode? = null,
    val place: JsonNode? = null, // 활동 장소 {name, address, lat, lng}(admin-v1.md "활동 장소와 지오코딩"). 생략은 유지, JSON null은 삭제
    val tagIds: List<Long>? = null, // 활동 태그 전체(native-v1.md "태그"). 생략은 유지, 빈 배열은 모두 해제. 운영자 지정 tags로 보호
)
