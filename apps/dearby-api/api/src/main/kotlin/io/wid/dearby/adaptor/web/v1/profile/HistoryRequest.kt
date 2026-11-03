package io.wid.dearby.adaptor.web.v1.profile

import java.time.LocalDate
import java.util.*

// native-v1.md "이력 (History) — 역할 매칭": roleId(등록된 역할) 또는 직접 입력 ownerKind + (ownerId | ownerName) + roleName. 둘 다 보내면 roleId.
// 옛 kind·organizationId·organizationName·programId·activityId·*Name 필드는 받지 않는다
data class HistoryRequest(
    val id: UUID,
    val roleId: Long? = null,
    val ownerKind: String? = null,
    val ownerId: Long? = null,
    val ownerName: String? = null,
    val roleName: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val description: String = "",
)
