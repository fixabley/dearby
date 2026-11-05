package io.wid.dearby.auth.web

import io.wid.dearby.application.discord.DiscordAccountService
import io.wid.dearby.application.user.AccountDeletionService
import io.wid.dearby.application.user.SignInMethodService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

// 로그인한 사용자 자신의 계정. 인증 필요(api의 AccessRules)
@RestController
@RequestMapping("/v1/me")
class AccountController(
    private val accounts: AccountDeletionService,
    private val signInMethods: SignInMethodService,
    private val discord: DiscordAccountService,
) {

    // 설정 화면 "계정"(native-v1.md "설정"): 가입일과 로그인 수단 수, 로그인 수단이 아닌 연결(디스코드)
    @GetMapping
    fun me(@AuthenticationPrincipal jwt: Jwt): MeResponse {
        val userId = UUID.fromString(jwt.subject)
        val summary = signInMethods.summary(userId)
        val linked = discord.status(userId)
        return MeResponse(
            userId, summary.createdAt, SignInMethodsResponse(summary.passkeys, summary.apple, summary.google),
            ConnectionsResponse(DiscordConnectionResponse(linked.available, linked.account?.username, linked.account?.linkedAt)),
        )
    }

    // 되돌릴 수 없다. 앱은 확인을 받은 뒤 부르고, 성공하면 저장한 토큰을 지운다
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal jwt: Jwt) = accounts.delete(UUID.fromString(jwt.subject))
}
