package io.wid.dearby.adaptor.web.v1.cards

import io.wid.dearby.adaptor.web.v1.guest.GuestController
import io.wid.dearby.adaptor.web.v1.guest.GuestProperties
import io.wid.dearby.application.card.CardQrCodes
import io.wid.dearby.application.card.CardReportService
import io.wid.dearby.application.card.CardService
import io.wid.dearby.application.guest.GuestService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

// native-v1.md "명함·지갑"·"명함 QR 코드". GET /v1/cards/{id}는 보안 계층에서는 열려 있고(ApiAccessRules) 볼 근거는 CardService.view가 확인한다. 나머지는 주인만
@RestController
@RequestMapping("/v1/cards")
class CardController(
    private val cards: CardService, private val codes: CardQrCodes, private val reports: CardReportService, private val guests: GuestService, private val guestProperties: GuestProperties,
) {

    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt) = CardListResponse(cards.list(userId(jwt)).map { CardResponse.from(it, includeTemplate = true) })

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun publish(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: CardPublishRequest) =
        CardResponse.from(cards.publish(userId(jwt), request.name, request.description, request.contactIds, request.historyIds), includeTemplate = true)

    // 명함 고치기(native-v1.md "명함 고치기는 같은 명함을 수정"): 같은 id의 스냅샷을 현재 프로필로 다시 만든다. 남의·철회된 명함은 404
    @PutMapping("/{id}")
    fun update(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID, @RequestBody request: CardPublishRequest) =
        CardResponse.from(cards.update(userId(jwt), id, request.name, request.description, request.contactIds, request.historyIds), includeTemplate = true)

    // 근거: 주인·받은 계정(Bearer), 웹 프록시가 보낸 게스트 세션(게스트 경로와 같은 헤더), 또는 ?code=. 없으면 403 CARD_CODE_REQUIRED·CARD_CODE_EXPIRED
    @GetMapping("/{id}")
    fun get(
        @AuthenticationPrincipal jwt: Jwt?, @PathVariable id: UUID, @RequestParam(required = false) code: String?,
        @RequestHeader(GuestController.PROXY_KEY, required = false) key: String?, @RequestHeader(GuestController.TOKEN, required = false) token: String?,
    ): CardResponse {
        val digest = guests.sessionDigest(guestProperties.proxySecret, key, token)
        return CardResponse.from(cards.view(id, jwt?.let(::userId), guests.holds(digest, id), code), includeTemplate = false)
    }

    // 신고(native-v1.md "명함 신고·차단"): 볼 권한이 있는 계정·게스트·코드. 첫 신고 201, 같은 신고자의 24시간 안 재신고 200(기존 신고), 내 명함 400
    @PostMapping("/{id}/reports")
    fun report(
        @AuthenticationPrincipal jwt: Jwt?, @PathVariable id: UUID, @RequestBody request: CardReportRequest,
        @RequestHeader(GuestController.PROXY_KEY, required = false) key: String?, @RequestHeader(GuestController.TOKEN, required = false) token: String?,
    ): ResponseEntity<CardReportResponse> {
        val digest = guests.sessionDigest(guestProperties.proxySecret, key, token)
        val filed = reports.report(id, jwt?.let(::userId), digest, guests.holds(digest, id), request.code, request.reason, request.detail)
        return ResponseEntity.status(if (filed.created) HttpStatus.CREATED else HttpStatus.OK).body(CardReportResponse(filed.report.id, filed.report.reason.name, filed.report.createdAt))
    }

    // QR 화면을 열 때(native-v1.md "명함 QR 코드"). 내 명함이 아니거나 철회됐으면 404
    @PostMapping("/{id}/qr-codes")
    @ResponseStatus(HttpStatus.CREATED)
    fun issueQrCode(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID) = codes.issue(userId(jwt), id).let { QrCodeResponse(it.id, it.code, it.expiresAt) }

    // 닫기·새로고침: 2분 뒤 만료. 이미 만료여도 204
    @DeleteMapping("/{id}/qr-codes/{codeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun closeQrCode(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID, @PathVariable codeId: UUID) = codes.close(userId(jwt), id, codeId)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun revoke(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID) = cards.revoke(userId(jwt), id)


    private fun userId(jwt: Jwt): UUID = UUID.fromString(jwt.subject)
}
