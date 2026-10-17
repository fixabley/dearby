package io.wid.dearby.admin.application

import io.wid.dearby.application.profile.JobRepository
import io.wid.dearby.application.profile.ProfileJobForReview
import io.wid.dearby.application.profile.ProfileJobReviewRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.profile.ApprovalStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

// 직접 입력한 직무 승인(admin-v1.md "직무"). 목록은 id 오름차순 keyset 페이지
@Service
class JobReviewService(private val entries: ProfileJobReviewRepository, private val jobs: JobRepository) {

    @Transactional(readOnly = true)
    fun search(approval: ApprovalStatus?, cursor: UUID?, size: Int): ReviewPage<ProfileJobForReview> {
        if (size !in 1..100) throw InvalidInputException("size는 1에서 100 사이여야 합니다")
        val found = entries.search(approval, cursor, size + 1)
        val page = found.take(size)
        return ReviewPage(page, if (found.size > size) page.last().entry.id else null)
    }

    // 승인 때 jobId를 주면 직접 입력 이름을 그 직무로 바꾼다. 거절은 사유가 필요하다
    @Transactional
    fun decideApproval(id: UUID, approve: Boolean, jobId: Id?, reason: String?): ProfileJobForReview {
        require(id)
        if (approve) {
            jobId?.let { if (jobs.findAllById(listOf(it)).isEmpty()) throw InvalidInputException("직무를 찾을 수 없습니다", "Job not found: $it") }
            entries.updateApproval(id, ApprovalStatus.APPROVED, null, jobId)
        } else {
            val why = reason?.trim()?.ifEmpty { null } ?: throw InvalidInputException("거절 사유를 적어 주세요")
            if (why.length > 500) throw InvalidInputException("거절 사유는 500자까지입니다")
            entries.updateApproval(id, ApprovalStatus.REJECTED, why, null)
        }
        return require(id)
    }

    private fun require(id: UUID): ProfileJobForReview = entries.find(id) ?: throw NotFoundException("직무 항목을 찾을 수 없습니다", "Profile job not found: $id")
}
