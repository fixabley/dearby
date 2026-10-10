package io.wid.dearby.adaptor.web

import io.wid.dearby.adaptor.security.PasskeyOptions
import io.wid.dearby.adaptor.security.PasskeyService
import io.wid.dearby.adaptor.security.TokenPair
import io.wid.dearby.adaptor.security.TokenService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import tools.jackson.databind.JsonNode
import java.util.*

data class RefreshRequest(val refreshToken: String)

data class RegistrationOptionsRequest(val displayName: String? = null)

// credential은 WebAuthn 표준 JSON(RegistrationResponseJSON·AuthenticationResponseJSON)
data class PasskeyRequest(val challengeId: UUID, val credential: JsonNode)

// 모두 인증 없이 받는다(SecurityConfig). 세션 쿠키를 쓰지 않고 challengeId로 옵션 요청과 완료 요청을 잇는다
@RestController
@RequestMapping("/v1/auth")
class AuthController(private val tokens: TokenService, private val passkeys: PasskeyService) {

    @PostMapping("/passkeys/registration/options")
    fun registrationOptions(@RequestBody(required = false) request: RegistrationOptionsRequest?): PasskeyOptions =
        passkeys.registrationOptions(request?.displayName)

    @PostMapping("/passkeys/registration")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: PasskeyRequest): TokenPair =
        passkeys.register(request.challengeId, request.credential)

    @PostMapping("/passkeys/authentication/options")
    fun authenticationOptions(): PasskeyOptions = passkeys.authenticationOptions()

    @PostMapping("/passkeys/authentication")
    fun authenticate(@RequestBody request: PasskeyRequest): TokenPair =
        passkeys.authenticate(request.challengeId, request.credential)

    @PostMapping("/refresh")
    fun refresh(@RequestBody request: RefreshRequest): ResponseEntity<TokenPair> =
        tokens.refresh(request.refreshToken)?.let { ResponseEntity.ok(it) }
            ?: ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@RequestBody request: RefreshRequest) = tokens.revoke(request.refreshToken)
}
