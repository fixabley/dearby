package io.wid.dearby.auth.web

import io.wid.dearby.application.auth.GoogleSignInResult
import io.wid.dearby.application.auth.GoogleSignInService
import io.wid.dearby.application.auth.TokenPair
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

// 구글 로그인(native-v1.md "구글 로그인"). 웹 방식만 있다. 요청 본문은 애플 웹 방식과 같아 그 요청 타입을 함께 쓴다
@RestController
@RequestMapping("/v1/auth/google")
class GoogleAuthController(private val google: GoogleSignInService) {

    // 앱이 ASWebAuthenticationSession(iOS)·Custom Tab(Android)으로 열 구글 인가 주소. 결과는 콜백을 거쳐 dearby://auth/google로 돌아온다
    @PostMapping("/web/start")
    fun startWeb(@RequestBody request: AppleWebStartRequest) = AppleWebStartResponse(google.startWeb(request.verifierHash))

    // 구글이 사용자 브라우저를 GET으로 돌려보낸다(code·state 또는 error). 결과(1회용 code 또는 error)를 붙여 앱으로 돌려보낸다
    @GetMapping("/callback")
    fun callback(
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> =
        ResponseEntity.status(HttpStatus.SEE_OTHER).location(google.completeWeb(state, code, error)).build()

    // 200 TokenPair(연결된 계정) 또는 202 GoogleNeedsAccountResponse(처음 보는 계정)
    @PostMapping("/web/exchange")
    fun exchangeWeb(@RequestBody request: AppleWebExchangeRequest): ResponseEntity<Any> = when (val result = google.exchangeWeb(request.code, request.verifier)) {
        is GoogleSignInResult.SignedIn -> ResponseEntity.ok(result.tokens)
        is GoogleSignInResult.NeedsAccount -> ResponseEntity.status(HttpStatus.ACCEPTED).body(GoogleNeedsAccountResponse(result.linkToken, result.email, result.displayName))
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: AppleLinkTokenRequest): TokenPair = google.register(request.linkToken)
}
