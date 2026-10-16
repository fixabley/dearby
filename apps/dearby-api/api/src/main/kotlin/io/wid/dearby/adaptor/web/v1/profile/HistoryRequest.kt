package io.wid.dearby.adaptor.web.v1.profile

import java.time.LocalDate
import java.util.*

// 카탈로그 id와 직접 입력 이름을 함께 보내면 id를 쓴다
data class HistoryRequest(
    val id: UUID,
    val kind: String,
    val organizationId: Long? = null,
    val organizationName: String? = null,
    val programId: Long? = null,
    val programName: String? = null,
    val activityId: Long? = null,
    val activityName: String? = null,
    val roleId: Long? = null,
    val roleName: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val description: String = "",
)
