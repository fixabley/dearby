package io.wid.dearby.adaptor.web.v1.guest

import io.wid.dearby.adaptor.web.v1.cards.CardResponse
import io.wid.dearby.application.guest.GuestSaveResult
import io.wid.dearby.application.guest.GuestService
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

// 웹 비로그인 저장(native-v1.md "웹 비로그인 명함 저장"·"홈 화면 세션 잇기").
// 로그인 대신 Next 서버만 아는 프록시 키와 세션 토큰 헤더를 본다. 응답의 Cache-Control: no-store는 보안 필터 기본 헤더가 붙인다
@RestController
@RequestMapping("/v1/guest")
@EnableConfigurationProperties(GuestProperties::class)
class GuestController(private val guests: GuestService, private val properties: GuestProperties) {

    @GetMapping("/cards")
    fun list(@RequestHeader(PROXY_KEY, required = false) key: String?, @RequestHeader(TOKEN, required = false) token: String?): GuestWalletResponse {
        guests.admit(properties.proxySecret, key)
        val wallet = guests.wallet(token)
        return GuestWalletResponse(wallet.items.map { CardResponse.from(it, includeTemplate = false) })
    }

    // 토큰 없는 첫 저장은 세션을 만들어 201과 guestToken(Next만 쿠키로 소비)
    @PutMapping("/cards/{id}")
    fun saveCard(@RequestHeader(PROXY_KEY, required = false) key: String?, @RequestHeader(TOKEN, required = false) token: String?, @PathVariable id: UUID): ResponseEntity<GuestSaveResponse> {
        guests.admit(properties.proxySecret, key)
        return saved(guests.saveCard(token, id))
    }


    @DeleteMapping("/cards/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun removeCard(@RequestHeader(PROXY_KEY, required = false) key: String?, @RequestHeader(TOKEN, required = false) token: String?, @PathVariable id: UUID) {
        guests.admit(properties.proxySecret, key)
        guests.removeCard(token, id)
    }

    @DeleteMapping("/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSession(@RequestHeader(PROXY_KEY, required = false) key: String?, @RequestHeader(TOKEN, required = false) token: String?) {
        guests.admit(properties.proxySecret, key)
        guests.deleteSession(token)
    }

    @PostMapping("/handoffs")
    @ResponseStatus(HttpStatus.CREATED)
    fun issueHandoff(@RequestHeader(PROXY_KEY, required = false) key: String?, @RequestHeader(TOKEN, required = false) token: String?): HandoffResponse {
        guests.admit(properties.proxySecret, key)
        val (code, expiresAt) = guests.issueHandoff(token)
        return HandoffResponse(code, expiresAt)
    }

    // 토큰 없이 부른다. 실패도 요청 제한에 센다
    @PostMapping("/handoffs/redeem")
    fun redeemHandoff(@RequestHeader(PROXY_KEY, required = false) key: String?, @RequestBody request: HandoffRedeemRequest): HandoffRedeemResponse {
        guests.admit(properties.proxySecret, key)
        return HandoffRedeemResponse(guests.redeemHandoff(request.code))
    }

    private fun saved(result: GuestSaveResult) = ResponseEntity.status(if (result.guestToken != null) HttpStatus.CREATED else HttpStatus.OK)
        .body(GuestSaveResponse(result.cardId, result.status, result.guestToken))

    companion object {
        const val PROXY_KEY = "X-Guest-Proxy-Key"
        const val TOKEN = "X-Guest-Token"
    }
}
