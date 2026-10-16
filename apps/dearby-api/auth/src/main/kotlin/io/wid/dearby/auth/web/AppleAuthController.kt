package io.wid.dearby.auth.web

import io.wid.dearby.application.auth.AppleSignInResult
import io.wid.dearby.application.auth.AppleSignInService
import io.wid.dearby.application.auth.TokenPair
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.json.JsonMapper

// 애플 로그인(native-v1.md "애플 로그인"). 인증 없이 받는다(SecurityConfig의 /v1/auth/**)
@RestController
@RequestMapping("/v1/auth/apple")
class AppleAuthController(private val apple: AppleSignInService) {

    private val json = JsonMapper.builder().build()

    @PostMapping("/nonce")
    fun nonce(): AppleNonceResponse = apple.nonce().let { (challengeId, nonce) -> AppleNonceResponse(challengeId, nonce) }

    // 200 TokenPair(연결된 계정) 또는 202 AppleNeedsAccountResponse(처음 보는 계정)
    @PostMapping
    fun signIn(@RequestBody request: AppleSignInRequest): ResponseEntity<Any> =
        respond(apple.signIn(request.challengeId, request.identityToken, request.displayName))

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: AppleLinkTokenRequest): TokenPair = apple.register(request.linkToken)

    // 웹 방식(Android): 앱이 Custom Tab으로 열 애플 인가 주소. 결과는 콜백을 거쳐 앱 주소(dearby://auth/apple)로 돌아온다
    @PostMapping("/web/start")
    fun startWeb(@RequestBody request: AppleWebStartRequest) = AppleWebStartResponse(apple.startWeb(request.verifierHash))

    // 애플이 사용자 브라우저에서 form_post로 보낸다. 결과(1회용 code 또는 error)를 붙여 앱으로 돌려보낸다
    @PostMapping("/callback", consumes = [MediaType.APPLICATION_FORM_URLENCODED_VALUE])
    fun callback(
        @RequestParam(required = false) state: String?,
        @RequestParam("id_token", required = false) idToken: String?,
        @RequestParam(required = false) user: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.SEE_OTHER).location(apple.completeWeb(state, idToken, nameFrom(user), error)).build()

    @PostMapping("/web/exchange")
    fun exchangeWeb(@RequestBody request: AppleWebExchangeRequest): ResponseEntity<Any> =
        respond(apple.exchangeWeb(request.code, request.verifier))

    private fun respond(result: AppleSignInResult): ResponseEntity<Any> = when (result) {
        is AppleSignInResult.SignedIn -> ResponseEntity.ok(result.tokens)
        is AppleSignInResult.NeedsAccount -> ResponseEntity.status(HttpStatus.ACCEPTED).body(AppleNeedsAccountResponse(result.linkToken, result.email))
    }

    // 첫 인가 때만 오는 user = {"name":{"firstName","lastName"},"email"}. 한글 이름은 성+이름을 붙여 쓴다
    private fun nameFrom(user: String?): String? {
        val name = user?.let { runCatching { json.readTree(it).path("name") }.getOrNull() } ?: return null
        val first = name.path("firstName").asString("").trim()
        val last = name.path("lastName").asString("").trim()
        if (first.isEmpty() && last.isEmpty()) return null
        return if ((first + last).any { it in '가'..'힣' }) last + first else listOf(first, last).filter { it.isNotEmpty() }.joinToString(" ")
    }
}
