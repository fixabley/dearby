package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.application.profile.HistoryForReview
import io.wid.dearby.domain.profile.History
import java.time.LocalDate
import java.util.*

// native-v1.md "이력"에 작성자 프로필 이름을 더한 것. 계정 id는 싣지 않는다
data class HistoryReviewResponse(
    val id: UUID,
    val profileName: String,
    val kind: String,
    val organization: ReferenceResponse?,
    val organizationName: String?,
    val program: ReferenceResponse?,
    val programName: String?,
    val activity: ReferenceResponse?,
    val activityName: String?,
    val role: ReferenceResponse?,
    val roleName: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val description: String,
    val approval: String,
    val approvalReason: String?,
    val verification: String,
    val evidence: List<EvidenceReviewResponse>,
) {
    companion object {
        fun from(item: HistoryForReview): HistoryReviewResponse {
            val h: History = item.history
            return HistoryReviewResponse(
                id = h.id,
                profileName = item.profileName,
                kind = h.kind.name.lowercase(),
                organization = h.organization?.let { ReferenceResponse(it.id, it.name) },
                organizationName = h.organizationName,
                program = h.program?.let { ReferenceResponse(it.id, it.name) },
                programName = h.programName,
                activity = h.activity?.let { ReferenceResponse(it.id, it.name) },
                activityName = h.activityName,
                role = h.role?.let { ReferenceResponse(it.id, it.name) },
                roleName = h.roleName,
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
