package io.wid.dearby.admin.application

import io.wid.dearby.application.activity.ActivityRepository
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.profile.EvidenceRepository
import io.wid.dearby.application.profile.HistoryForReview
import io.wid.dearby.application.profile.HistoryReviewRepository
import io.wid.dearby.application.profile.RoleRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.PublicationStatus
import io.wid.dearby.domain.profile.ApprovalStatus
import io.wid.dearby.domain.profile.Evidence
import io.wid.dearby.domain.profile.VerificationStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

// 이력 검토(native-v1.md "관리자"): 직접 입력 승인과 인증자료 확인. 목록은 id 오름차순 keyset 페이지
@Service
class HistoryReviewService(
    private val histories: HistoryReviewRepository,
    private val evidence: EvidenceRepository,
    private val organizations: OrganizationRepository,
    private val programs: ProgramRepository,
    private val activities: ActivityRepository,
    private val roles: RoleRepository,
) {

    @Transactional(readOnly = true)
    fun search(approval: ApprovalStatus?, verification: VerificationStatus?, cursor: UUID?, size: Int): ReviewPage<HistoryForReview> {
        if (size !in 1..MAX_PAGE_SIZE) throw InvalidInputException("size는 1에서 ${MAX_PAGE_SIZE} 사이여야 합니다")
        val found = histories.search(approval, verification, cursor, size + 1)
        val page = found.take(size)
        return ReviewPage(page, if (found.size > size) page.last().history.id else null)
    }

    // 승인 때 준 id는 직접 입력 값을 대신한다. 없는 카탈로그 id는 400. 거절은 사유가 필요하다
    @Transactional
    fun decideApproval(historyId: UUID, approve: Boolean, organizationId: Id?, programId: Id?, activityId: Id?, roleId: Id?, reason: String?): HistoryForReview {
        require(historyId)
        if (approve) {
            organizationId?.let { if (organizations.findAllById(listOf(it)).isEmpty()) throw InvalidInputException("조직을 찾을 수 없습니다", "Organization not found: $it") }
            programId?.let { if (programs.findAllById(listOf(it)).isEmpty()) throw InvalidInputException("프로그램을 찾을 수 없습니다", "Program not found: $it") }
            activityId?.let { if (activities.findById(it, PublicationStatus.PUBLISHED) == null) throw InvalidInputException("활동을 찾을 수 없습니다", "Activity not found: $it") }
            roleId?.let { if (roles.findAllById(listOf(it)).isEmpty()) throw InvalidInputException("역할을 찾을 수 없습니다", "Role not found: $it") }
            histories.updateApproval(historyId, ApprovalStatus.APPROVED, null, organizationId, programId, activityId, roleId)
        } else {
            val why = reason?.trim()?.ifEmpty { null } ?: throw InvalidInputException("거절 사유를 적어 주세요")
            if (why.length > MAX_REASON) throw InvalidInputException("거절 사유는 ${MAX_REASON}자까지입니다")
            histories.updateApproval(historyId, ApprovalStatus.REJECTED, why, null, null, null, null)
        }
        return require(historyId)
    }

    @Transactional
    fun decideVerification(historyId: UUID, verify: Boolean): HistoryForReview {
        require(historyId)
        histories.updateVerification(historyId, if (verify) VerificationStatus.VERIFIED else VerificationStatus.REJECTED)
        return require(historyId)
    }

    @Transactional(readOnly = true)
    fun evidence(historyId: UUID, evidenceId: UUID): Evidence {
        require(historyId)
        return evidence.find(historyId, evidenceId) ?: throw NotFoundException("인증자료를 찾을 수 없습니다", "Evidence not found: $evidenceId")
    }

    private fun require(historyId: UUID): HistoryForReview =
        histories.find(historyId) ?: throw NotFoundException("이력을 찾을 수 없습니다", "History not found: $historyId")

    private companion object {
        const val MAX_PAGE_SIZE = 100
        const val MAX_REASON = 500
    }
}
