package io.wid.dearby.application.profile

import io.wid.dearby.domain.Id
import java.time.LocalDate
import java.util.*

// PUT /v1/profile의 이력. roleId가 있으면 그 역할(귀속 단위도 역할에서), 없으면 직접 입력 ownerKind + (ownerId | ownerName) + roleName
data class HistoryInput(
    val id: UUID,
    val roleId: Id?,
    val ownerKind: String?,
    val ownerId: Id?,
    val ownerName: String?,
    val roleName: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val description: String,
)
