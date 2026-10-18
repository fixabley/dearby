package io.wid.dearby.adaptor.web.v1.wallet

import io.wid.dearby.application.card.WalletService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

// 받은 명함(지갑). 로그인 사용자 자신의 것만
@RestController
@RequestMapping("/v1/wallet")
class WalletController(private val wallet: WalletService) {

    @GetMapping
    fun list(@AuthenticationPrincipal jwt: Jwt) = WalletResponse(wallet.list(userId(jwt)).map(ReceiptResponse::from))


    private fun userId(jwt: Jwt): UUID = UUID.fromString(jwt.subject)
}
