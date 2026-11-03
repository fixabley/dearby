package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.domain.profile.History
import io.wid.dearby.domain.profile.RoleOwner
import java.time.LocalDate
import java.util.*

// native-v1.md "이력 (History) — 역할 매칭". 상태 값은 소문자. organization·program·activity는 귀속에서 서버가 채우는 편의 값. 명함에 실린 이력은 evidence가 빈 목록이다
data class HistoryResponse(
    val id: UUID,
    val role: ActivityInfoRefResponse?,
    val roleName: String,
    val owner: OwnerResponse,
    val organization: ActivityInfoRefResponse?,
    val program: ActivityInfoRefResponse?,
    val activity: ActivityInfoRefResponse?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val description: String,
    val approval: String,
    val approvalReason: String?,
    val verification: String,
    val evidence: List<EvidenceResponse>,
) {
    // id가 null이면 사용자가 이름만 직접 적은 단위(승인 대기)
    data class OwnerResponse(val kind: String, val id: Long?, val name: String) {
        companion object {
            fun from(owner: RoleOwner) = OwnerResponse(owner.kind.name.lowercase(), owner.id, owner.name)
        }
    }

    companion object {
        fun from(h: History) = HistoryResponse(
            id = h.id,
            role = h.role?.let(ActivityInfoRefResponse::from),
            roleName = h.roleName,
            owner = OwnerResponse.from(h.owner),
            organization = h.organization?.let(ActivityInfoRefResponse::from),
            program = h.program?.let(ActivityInfoRefResponse::from),
            activity = h.activity?.let(ActivityInfoRefResponse::from),
            startDate = h.startDate,
            endDate = h.endDate,
            description = h.description,
            approval = h.approval.name.lowercase(),
            approvalReason = h.approvalReason,
            verification = h.verification.name.lowercase(),
            evidence = h.evidence.map(EvidenceResponse::from),
        )
    }
}
