package io.wid.dearby.adaptor.web.v1.wallet

import io.wid.dearby.application.card.WalletService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

// 받은 명함(지갑, native-v1.md "명함 교환"). 로그인 사용자 자신의 것만
@RestController
@RequestMapping("/v1/wallet")
class WalletController(private val wallet: WalletService) {

    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt) = WalletResponse(wallet.list(userId(jwt)).map(ReceiptResponse::from))

    // 첫 저장 201, 이미 있으면 200(받은 날 유지). 없는·철회된 명함 404, 자기 명함 409 OWN_CARD, 처음 저장인데 코드가 없거나 만료면 403
    @PutMapping("/cards/{cardId}")
    fun save(@AuthenticationPrincipal jwt: Jwt, @PathVariable cardId: UUID, @RequestBody(required = false) request: WalletSaveRequest?): ResponseEntity<ReceiptResponse> {
        val saved = wallet.save(userId(jwt), cardId, request?.activityId, request?.code)
        return ResponseEntity.status(if (saved.created) HttpStatus.CREATED else HttpStatus.OK).body(ReceiptResponse.from(saved.receipt))
    }

    // 받은 명함의 주인에게 내 명함을 같은 활동으로 건넨다(맞교환). 받은 적 없으면 403, 내 명함이 아니면 404
    @PostMapping("/cards/{cardId}/reciprocate")
    @ResponseStatus(HttpStatus.CREATED)
    fun reciprocate(@AuthenticationPrincipal jwt: Jwt, @PathVariable cardId: UUID, @RequestBody request: ReciprocateRequest): ReciprocateResponse {
        wallet.reciprocate(userId(jwt), cardId, request.myCardId)
        return ReciprocateResponse(true)
    }

    // 차단(native-v1.md "명함 신고·차단"): 그 명함 주인의 명함을 내 지갑에서 모두 빼고 기록한다. 이미 차단했으면 그 기록(201)
    @PostMapping("/blocks")
    @ResponseStatus(HttpStatus.CREATED)
    fun block(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: BlockRequest) = BlockResponse.from(wallet.block(userId(jwt), request.cardId))

    @GetMapping("/blocks")
    fun blocks(@AuthenticationPrincipal jwt: Jwt) = BlockListResponse(wallet.blocks(userId(jwt)).map(BlockResponse::from))

    @DeleteMapping("/blocks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unblock(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID) = wallet.unblock(userId(jwt), id)

    private fun userId(jwt: Jwt): UUID = UUID.fromString(jwt.subject)
}
