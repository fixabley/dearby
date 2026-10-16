package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.domain.profile.History
import java.time.LocalDate
import java.util.*

// native-v1.md "이력". 상태 값은 소문자. 명함에 실린 이력은 evidence가 빈 목록이다
data class HistoryResponse(
    val id: UUID,
    val kind: String,
    val organization: ActivityInfoRefResponse?,
    val organizationName: String?,
    val program: ActivityInfoRefResponse?,
    val programName: String?,
    val activity: ActivityInfoRefResponse?,
    val activityName: String?,
    val role: ActivityInfoRefResponse?,
    val roleName: String?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val description: String,
    val approval: String,
    val approvalReason: String?,
    val verification: String,
    val evidence: List<EvidenceResponse>,
) {
    companion object {
        fun from(h: History) = HistoryResponse(
            id = h.id,
            kind = h.kind.name.lowercase(),
            organization = h.organization?.let(ActivityInfoRefResponse::from),
            organizationName = h.organizationName,
            program = h.program?.let(ActivityInfoRefResponse::from),
            programName = h.programName,
            activity = h.activity?.let(ActivityInfoRefResponse::from),
            activityName = h.activityName,
            role = h.role?.let(ActivityInfoRefResponse::from),
            roleName = h.roleName,
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
