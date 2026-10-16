package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.HistoryReviewService
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.profile.ApprovalStatus
import io.wid.dearby.domain.profile.VerificationStatus
import org.springframework.http.ContentDisposition
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

// native-v1.md "관리자": 이력 승인·인증 검토. ADMIN만(AdminAccessRules)
@RestController
@RequestMapping("/v1/admin/histories")
class HistoryReviewController(private val review: HistoryReviewService) {

    @GetMapping
    fun search(
        @RequestParam(required = false) approval: String?,
        @RequestParam(required = false) verification: String?,
        @RequestParam(required = false) cursor: UUID?,
        @RequestParam(defaultValue = "20") size: Int,
    ) = review.search(
        approval?.let { parse(it, ApprovalStatus.entries, "approval") },
        verification?.let { parse(it, VerificationStatus.entries, "verification") },
        cursor,
        size,
    ).let { page -> ReviewPageResponse(page.items.map(HistoryReviewResponse::from), page.nextCursor) }

    @PostMapping("/{id}/approval")
    fun approval(@PathVariable id: UUID, @RequestBody request: ApprovalDecisionRequest): HistoryReviewResponse {
        val approve = when (request.decision) {
            "approve" -> true
            "reject" -> false
            else -> throw InvalidInputException("decision은 approve 또는 reject입니다")
        }
        return HistoryReviewResponse.from(review.decideApproval(id, approve, request.organizationId, request.programId, request.activityId, request.roleId, request.reason))
    }

    @PostMapping("/{id}/verification")
    fun verification(@PathVariable id: UUID, @RequestBody request: VerificationDecisionRequest): HistoryReviewResponse {
        val verify = when (request.decision) {
            "verify" -> true
            "reject" -> false
            else -> throw InvalidInputException("decision은 verify 또는 reject입니다")
        }
        return HistoryReviewResponse.from(review.decideVerification(id, verify))
    }

    @GetMapping("/{id}/evidence/{evidenceId}")
    fun evidence(@PathVariable id: UUID, @PathVariable evidenceId: UUID): ResponseEntity<ByteArray> {
        val file = review.evidence(id, evidenceId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(file.meta.contentType))
            .header("Content-Disposition", ContentDisposition.inline().filename(file.meta.fileName, Charsets.UTF_8).build().toString())
            .body(file.data)
    }

    private fun <E : Enum<E>> parse(value: String, entries: List<E>, name: String): E =
        entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: throw InvalidInputException("$name 값이 올바르지 않습니다")
}
