package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.JobReviewService
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.profile.ApprovalStatus
import org.springframework.web.bind.annotation.*
import java.util.*

// 직접 입력한 직무 승인(admin-v1.md "직무"). ADMIN만
@RestController
@RequestMapping("/v1/admin/job-entries")
class JobReviewController(private val review: JobReviewService) {

    @GetMapping
    fun search(
        @RequestParam(required = false) approval: String?,
        @RequestParam(required = false) cursor: UUID?,
        @RequestParam(defaultValue = "20") size: Int,
    ) = review.search(
        approval?.let { value -> ApprovalStatus.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: throw InvalidInputException("approval 값이 올바르지 않습니다") },
        cursor,
        size,
    ).let { page -> ReviewPageResponse(page.items.map(JobEntryReviewResponse::from), page.nextCursor) }

    @PostMapping("/{id}/approval")
    fun approval(@PathVariable id: UUID, @RequestBody request: JobApprovalRequest): JobEntryReviewResponse {
        val approve = when (request.decision) {
            "approve" -> true
            "reject" -> false
            else -> throw InvalidInputException("decision은 approve 또는 reject입니다")
        }
        return JobEntryReviewResponse.from(review.decideApproval(id, approve, request.jobId, request.reason))
    }
}
