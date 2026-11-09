package io.wid.dearby.domain.profile

import java.time.LocalDate
import java.util.*

// 이력 = 사용자가 역할 하나에 매칭된 것(native-v1.md "이력 (History) — 역할 매칭"). 조직·프로그램·활동은 역할의 귀속에서 따라온다.
// 등록된 역할이면 role, 직접 적었으면 roleName만(승인 대기). 등록된 단위가 없으면 owner.id가 null이고 owner.name에 직접 적은 이름(승인 대기)
data class History(
    val id: UUID,
    val role: ActivityInfoRef?,
    val roleName: String,
    val owner: RoleOwner,
    val organization: ActivityInfoRef?, // 귀속에서 파생: 활동 역할이면 셋 다, 프로그램 역할이면 조직·프로그램, 조직 역할이면 조직만
    val program: ActivityInfoRef?,
    val activity: ActivityInfoRef?,
    val startDate: LocalDate,
    val endDate: LocalDate?, // 진행 중이면 null
    val description: String,
    val approval: ApprovalStatus,
    val approvalReason: String?,
    val verification: VerificationStatus,
    val evidence: List<EvidenceMeta>,
) {
    // 직접 입력(관리자 승인 대상): 역할 이름만 적었거나 단위 이름만 적었다
    val hasCustomEntry: Boolean
        get() = role == null || owner.id == null

    // 승인 여부 판단에 쓰는 직접 입력 값들(바뀌면 다시 PENDING)
    val customEntries: List<String>
        get() = listOf(owner.kind.name, owner.id?.toString() ?: owner.name, role?.id?.toString() ?: roleName)
}
