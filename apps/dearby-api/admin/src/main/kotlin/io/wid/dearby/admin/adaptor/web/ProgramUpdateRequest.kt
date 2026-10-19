package io.wid.dearby.admin.adaptor.web

import tools.jackson.databind.JsonNode

// 보낸 필드만 바꾼다. nameEn은 빈 문자열이면 지운다. category(admin-v1.md "프로그램 기본 종류"): 생략은 유지, null은 해제, 코드는 지정.
// applyCategoryToActivities가 true이고 종류가 있으면 운영자가 지정하지 않은 활동의 종류를 함께 바꾼다
data class ProgramUpdateRequest(
    val name: String? = null,
    val nameEn: String? = null,
    val organizationId: Long? = null,
    val category: JsonNode? = null,
    val applyCategoryToActivities: Boolean = false,
)
