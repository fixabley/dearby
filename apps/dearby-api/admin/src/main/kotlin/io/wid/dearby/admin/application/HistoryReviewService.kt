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
import io.wid.dearby.domain.profile.RoleOwnerKind
import io.wid.dearby.domain.profile.VerificationStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

// 이력 검토(admin-v1.md "이력 검토·역할"): 직접 입력 승인과 인증자료 확인. 목록은 id 오름차순 keyset 페이지
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

    // 승인: 이름만 적은 단위는 owner(ownerKind·ownerId)로 등록된 단위를 골라야 하고, 역할은 roleId(그 단위의 역할)나 이미 등록된 역할이어야 한다. 거절은 사유가 필요하다
    @Transactional
    fun decideApproval(historyId: UUID, approve: Boolean, ownerKind: String?, ownerId: Id?, roleId: Id?, reason: String?): HistoryForReview {
        val history = require(historyId).history
        if (approve) {
            val owner = owner(ownerKind, ownerId)
            val (kind, id) = owner ?: (history.owner.id?.let { history.owner.kind to it } ?: throw InvalidInputException("직접 적은 단위입니다. ownerKind·ownerId로 등록된 조직·프로그램·활동을 골라 주세요"))
            if (roleId != null) {
                val role = roles.findAllById(listOf(roleId)).singleOrNull() ?: throw InvalidInputException("역할을 찾을 수 없습니다", "Role not found: $roleId")
                if (role.owner.kind != kind || role.owner.id != id) throw InvalidInputException("이력의 귀속 단위에 속한 역할이 아닙니다", "Role $roleId owner ${role.owner.kind}/${role.owner.id} != $kind/$id")
            } else if (history.role == null || owner != null) {
                throw InvalidInputException("직접 적은 역할입니다. 그 단위의 역할을 만들어 roleId로 주세요")
            }
            histories.updateApproval(historyId, ApprovalStatus.APPROVED, null, owner, roleId)
        } else {
            val why = reason?.trim()?.ifEmpty { null } ?: throw InvalidInputException("거절 사유를 적어 주세요")
            if (why.length > MAX_REASON) throw InvalidInputException("거절 사유는 ${MAX_REASON}자까지입니다")
            histories.updateApproval(historyId, ApprovalStatus.REJECTED, why, null, null)
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

    // ownerKind·ownerId는 함께 와야 하고 등록된 단위여야 한다(활동은 게시된 것만)
    private fun owner(ownerKind: String?, ownerId: Id?): Pair<RoleOwnerKind, Id>? {
        if (ownerKind == null && ownerId == null) return null
        val kind = RoleOwnerKind.entries.firstOrNull { it.name.equals(ownerKind, ignoreCase = true) } ?: throw InvalidInputException("ownerKind는 organization·program·activity 중 하나입니다")
        val id = ownerId ?: throw InvalidInputException("ownerId를 주세요")
        val found = when (kind) {
            RoleOwnerKind.ORGANIZATION -> organizations.findAllById(listOf(id)).isNotEmpty()
            RoleOwnerKind.PROGRAM -> programs.findAllById(listOf(id)).isNotEmpty()
            RoleOwnerKind.ACTIVITY -> activities.findById(id, PublicationStatus.PUBLISHED) != null
        }
        if (!found) throw InvalidInputException("귀속 단위를 찾을 수 없습니다", "${kind.name} not found: $id")
        return kind to id
    }

    private fun require(historyId: UUID): HistoryForReview =
        histories.find(historyId) ?: throw NotFoundException("이력을 찾을 수 없습니다", "History not found: $historyId")

    private companion object {
        const val MAX_PAGE_SIZE = 100
        const val MAX_REASON = 500
    }
}
