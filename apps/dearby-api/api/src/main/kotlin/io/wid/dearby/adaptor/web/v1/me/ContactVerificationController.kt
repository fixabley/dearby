package io.wid.dearby.adaptor.web.v1.me

import io.wid.dearby.adaptor.web.v1.profile.ProfileResponse
import io.wid.dearby.application.profile.GitHubContactLinkService
import io.wid.dearby.application.profile.LinkPlatform
import io.wid.dearby.application.profile.ProfileService
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.profile.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.util.*

// 연락처 인증(native-v1.md "연락처 인증"): GitHub 로그인 연결. 콜백만 인증 없이 받고(ApiAccessRules) 나머지는 로그인 사용자 자신의 것
@RestController
@RequestMapping("/v1/me/contacts")
class ContactVerificationController(
    private val github: GitHubContactLinkService,
    private val profiles: ProfileService,
) {

    @PostMapping("/github/start")
    fun startGitHub(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: GitHubStartRequest): GitHubStartResponse {
        val platform = LinkPlatform.entries.firstOrNull { it.name.equals(request.platform, ignoreCase = true) }
            ?: throw InvalidInputException("platform은 ios·android·web 중 하나입니다")
        return GitHubStartResponse(github.start(userId(jwt), platform))
    }

    // GitHub이 사용자 브라우저를 돌려보낸다. 결과(1회용 code 또는 error)를 붙여 앱·웹으로 303
    @GetMapping("/github/callback")
    fun githubCallback(
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.SEE_OTHER).location(github.callback(state, code, error)).build()

    @PostMapping("/github/complete")
    fun completeGitHub(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: GitHubCompleteRequest): ProfileResponse =
        response(userId(jwt), github.complete(userId(jwt), request.code))

    data class GitHubStartRequest(val platform: String)

    data class GitHubStartResponse(val authorizeUrl: String)

    data class GitHubCompleteRequest(val code: UUID)


    private fun response(userId: UUID, profile: Profile) = ProfileResponse.from(profile, profiles.consent(userId), profiles.photoUpdatedAt(userId)?.let(ProfileResponse::photoUrl))

    private fun userId(jwt: Jwt): UUID = UUID.fromString(jwt.subject)
}
