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
    val cost: String? = null,
)
