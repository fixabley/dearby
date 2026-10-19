package io.wid.dearby.admin.adaptor.web

import tools.jackson.databind.JsonNode

// admin-v1.md ActivityCreateInput(2026-10-19). 일정은 {startDate?, endDate?, startAt?, endAt?, dateLabel?} 객체, eligibility는 조건 트리
data class ActivityCreateRequest(
    val programId: Long? = null,
    val name: String? = null,
    val nameEn: String? = null,
    val description: String? = null,
    val descriptionEn: String? = null,
    val officialUrl: String? = null,
    val applicationUrl: String? = null,
    val participationType: String? = null,
    val category: String? = null,
    val activitySchedule: JsonNode? = null,
    val application: JsonNode? = null,
    val eligibility: JsonNode? = null,
    val audience: String? = null,
    val location: String? = null,
    val place: JsonNode? = null, // 활동 장소 {name, address, lat, lng}(admin-v1.md "활동 장소와 지오코딩")
    val cost: String? = null,
    val tagIds: List<Long>? = null, // 태그 id 목록(native-v1.md "태그"). 생략하면 프로그램 태그를 기본으로
)
