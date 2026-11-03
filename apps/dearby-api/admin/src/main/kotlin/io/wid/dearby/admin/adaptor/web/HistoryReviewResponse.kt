package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.application.profile.HistoryForReview
import io.wid.dearby.domain.profile.History
import java.time.LocalDate
import java.util.*

// native-v1.md "이력 (History) — 역할 매칭"의 History에 작성자 프로필 이름을 더한 것. 계정 id는 싣지 않는다
data class HistoryReviewResponse(
    val id: UUID,
    val profileName: String,
    val role: ReferenceResponse?,
    val roleName: String,
    val owner: OwnerResponse,
    val organization: ReferenceResponse?,
    val program: ReferenceResponse?,
    val activity: ReferenceResponse?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val description: String,
    val approval: String,
    val approvalReason: String?,
    val verification: String,
    val evidence: List<EvidenceReviewResponse>,
) {
    // id가 null이면 사용자가 이름만 직접 적은 단위. 승인 때 ownerKind·ownerId로 등록된 단위를 골라 준다
    data class OwnerResponse(val kind: String, val id: Long?, val name: String)

    companion object {
        fun from(item: HistoryForReview): HistoryReviewResponse {
            val h: History = item.history
            return HistoryReviewResponse(
                id = h.id,
                profileName = item.profileName,
                role = h.role?.let { ReferenceResponse(it.id, it.name) },
                roleName = h.roleName,
                owner = OwnerResponse(h.owner.kind.name.lowercase(), h.owner.id, h.owner.name),
                organization = h.organization?.let { ReferenceResponse(it.id, it.name) },
                program = h.program?.let { ReferenceResponse(it.id, it.name) },
                activity = h.activity?.let { ReferenceResponse(it.id, it.name) },
                startDate = h.startDate,
                endDate = h.endDate,
                description = h.description,
                approval = h.approval.name.lowercase(),
                approvalReason = h.approvalReason,
                verification = h.verification.name.lowercase(),
                evidence = h.evidence.map { EvidenceReviewResponse(it.id, it.fileName, it.contentType, it.size, it.uploadedAt) },
            )
        }
    }
}
