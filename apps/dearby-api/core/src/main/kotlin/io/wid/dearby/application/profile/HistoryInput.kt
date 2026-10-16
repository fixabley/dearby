package io.wid.dearby.application.profile

import io.wid.dearby.domain.Id
import java.time.LocalDate
import java.util.*

// PUT /v1/profile의 이력. 카탈로그 id와 직접 입력 이름을 함께 보내면 id를 쓴다
data class HistoryInput(
    val id: UUID,
    val kind: String,
    val organizationId: Id?,
    val organizationName: String?,
    val programId: Id?,
    val programName: String?,
    val activityId: Id?,
    val activityName: String?,
    val roleId: Id?,
    val roleName: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val description: String,
)
