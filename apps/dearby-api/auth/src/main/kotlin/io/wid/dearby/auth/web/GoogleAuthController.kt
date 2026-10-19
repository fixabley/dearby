package io.wid.dearby.auth.web

import io.wid.dearby.application.auth.GoogleSignInResult
import io.wid.dearby.application.auth.GoogleSignInService
import io.wid.dearby.application.auth.TokenPair
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

// 구글 로그인(native-v1.md "구글 로그인 — 2026-10-19 네이티브 SDK로 전환"). 앱의 구글 SDK가 받은 ID 토큰을 검증한다. 인증 없이 받는다(SecurityConfig의 /v1/auth/**)
@RestController
@RequestMapping("/v1/auth/google")
class GoogleAuthController(private val google: GoogleSignInService) {

    // 앱이 SDK 요청(iOS GIDSignIn nonce, Android GetGoogleIdOption nonce)에 그대로 넣을 1회용 nonce. 허용 client id가 없으면 503
    @PostMapping("/nonce")
    fun nonce() = GoogleNonceResponse(google.nonce())

    // 200 TokenPair(연결된 계정) 또는 202 GoogleNeedsAccountResponse(처음 보는 계정). 검증 실패 401
    @PostMapping
    fun signIn(@RequestBody request: GoogleSignInRequest): ResponseEntity<Any> = when (val result = google.signIn(request.idToken, request.nonce)) {
        is GoogleSignInResult.SignedIn -> ResponseEntity.ok(result.tokens)
        is GoogleSignInResult.NeedsAccount -> ResponseEntity.status(HttpStatus.ACCEPTED).body(GoogleNeedsAccountResponse(result.linkToken, result.email, result.displayName))
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: AppleLinkTokenRequest): TokenPair = google.register(request.linkToken)
}
