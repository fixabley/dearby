package io.wid.dearby.domain.profile

import java.time.LocalDate
import java.util.*

// 단계별로 입력한 이력: 조직/활동 → 조직 → 프로그램 → (활동) → 역할 → 기간.
// 활동 정보에서 고르면 *Ref, 직접 입력이면 *Name만 있다. 직접 입력이 있으면 approval이 PENDING이라 명함에 넣을 수 없다
data class History(
    val id: UUID,
    val kind: HistoryKind,
    val organization: ActivityInfoRef?,
    val organizationName: String?,
    val program: ActivityInfoRef?,
    val programName: String?,
    val activity: ActivityInfoRef?,
    val activityName: String?,
    val role: ActivityInfoRef?,
    val roleName: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?, // 진행 중이면 null
    val description: String,
    val approval: ApprovalStatus,
    val approvalReason: String?,
    val verification: VerificationStatus,
    val evidence: List<EvidenceMeta>,
) {
    // 활동 정보 id 없이 이름만 적은 값이 하나라도 있는가(관리자 승인 대상)
    val hasCustomEntry: Boolean
        get() = (organization == null && organizationName != null) || (program == null && programName != null) ||
            (activity == null && activityName != null) || (role == null && roleName != null)

    // 승인 여부 판단에 쓰는 직접 입력 값들(바뀌면 다시 PENDING)
    val customEntries: List<String?>
        get() = listOf(organization?.id?.toString() ?: organizationName, program?.id?.toString() ?: programName,
            activity?.id?.toString() ?: activityName, role?.id?.toString() ?: roleName)
}
