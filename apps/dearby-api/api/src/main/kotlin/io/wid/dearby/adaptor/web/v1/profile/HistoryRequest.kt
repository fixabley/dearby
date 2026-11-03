package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.application.profile.HistoryInput
import java.time.LocalDate
import java.util.*

// native-v1.md "이력 (History) — 역할 매칭": roleId(등록된 역할) 또는 직접 입력 ownerKind + (ownerId | ownerName) + roleName. 둘 다 보내면 roleId.
// 호환(앱 갱신 전까지, 2026-11-03 밤 조율 결정): 옛 모양(kind·organizationId·organizationName·programId·programName·activityId·activityName)도 받아
// V67 이관과 같은 규칙으로 바꾼다: 더 구체적인 단계에 직접 적은 이름이 있으면 그 이름(활동 → 프로그램), 없으면 가장 구체적인 등록 단위, 그것도 없으면 조직 이름
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
    val kind: String? = null,
    val organizationId: Long? = null,
    val organizationName: String? = null,
    val programId: Long? = null,
    val programName: String? = null,
    val activityId: Long? = null,
    val activityName: String? = null,
) {
    fun toInput(): HistoryInput {
        val legacy = ownerKind == null && ownerId == null && ownerName == null
        val (kind, id, name) = when {
            !legacy -> Triple(ownerKind, ownerId, ownerName)
            activityId == null && !activityName.isNullOrBlank() -> Triple("activity", null, activityName)
            programId == null && !programName.isNullOrBlank() -> Triple("program", null, programName)
            activityId != null -> Triple("activity", activityId, null)
            programId != null -> Triple("program", programId, null)
            organizationId != null -> Triple("organization", organizationId, null)
            organizationName != null -> Triple("organization", null, organizationName)
            else -> Triple(null, null, null)
        }
        return HistoryInput(this.id, roleId, kind, id, name, roleName, startDate, endDate, description)
    }
}
