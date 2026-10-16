package io.wid.dearby.adaptor.web.v1.wallet

import io.wid.dearby.application.card.WalletService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

// 받은 명함(지갑). 로그인 사용자 자신의 것만
@RestController
@RequestMapping("/v1/wallet")
class WalletController(private val wallet: WalletService) {

    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt): WalletResponse {
        val (receipts, shares) = wallet.list(userId(jwt))
        return WalletResponse(receipts.map(ReceiptResponse::from), shares.map(WalletShareResponse::from))
    }

    // 명함을 처음 받으면 201, 이미 있으면 200
    @PutMapping("/shares/{id}")
    fun save(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID): ResponseEntity<WalletSaveResponse> {
        val result = wallet.save(userId(jwt), id)
        return ResponseEntity.status(if (result.created) HttpStatus.CREATED else HttpStatus.OK)
            .body(WalletSaveResponse(result.receiptId, result.cardId, result.shareId, result.status))
    }

    private fun userId(jwt: Jwt): UUID = UUID.fromString(jwt.subject)
}
