package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.CardReportAdminService
import io.wid.dearby.domain.card.CardReportListing
import io.wid.dearby.domain.card.CardSnapshot
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.util.*

// 명함 신고(admin-v1.md "명함 신고"). ADMIN만(AdminAccessRules)
@RestController
@RequestMapping("/v1/admin")
class CardReportAdminController(private val reports: CardReportAdminService) {

    @GetMapping("/card-reports")
    fun list(@RequestParam(required = false) status: String?) = CardReportListResponse(reports.list(status).map(CardReportResponse::from))

    @GetMapping("/card-reports/{id}/photo")
    fun photo(@PathVariable id: UUID): ResponseEntity<ByteArray> =
        ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore()).body(reports.photo(id))

    @PatchMapping("/card-reports/{id}")
    fun update(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID, @RequestBody request: CardReportUpdateRequest) =
        CardReportResponse.from(reports.update(id, request.status, request.note, adminId(jwt)))

    @PostMapping("/cards/{id}/withdraw")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun withdraw(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID, @RequestBody request: CardWithdrawRequest) = reports.withdraw(id, request.reportId, request.note, adminId(jwt))

    // 처리한 운영자(토큰 sub가 사용자 id가 아니면 비운다)
    private fun adminId(jwt: Jwt): UUID? = runCatching { UUID.fromString(jwt.subject) }.getOrNull()
}

data class CardReportUpdateRequest(val status: String? = null, val note: String? = null)

data class CardWithdrawRequest(val reportId: UUID? = null, val note: String? = null)

data class CardReportListResponse(val items: List<CardReportResponse>)

data class CardReportResponse(
    val id: UUID, val cardId: UUID, val cardSnapshot: CardSnapshot, val reason: String, val detail: String?, val createdAt: OffsetDateTime,
    val status: String, val note: String?, val reportCount: Int, val cardWithdrawn: Boolean,
) {
    companion object {
        fun from(it: CardReportListing) = with(it.report) {
            CardReportResponse(id, cardId, snapshot, reason.name, detail, createdAt, status.name, note, it.reportCount, it.cardWithdrawn)
        }
    }
}
