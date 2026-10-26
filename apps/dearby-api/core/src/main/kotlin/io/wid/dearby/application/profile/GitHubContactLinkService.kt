package io.wid.dearby.application.profile

import io.wid.dearby.domain.ContactKind
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.ServiceUnavailableException
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.Profile
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.util.*

// GitHub 로그인으로 연락처 GitHub 항목을 인증(native-v1.md "연락처 인증"). 애플 웹 방식처럼 start → 콜백 → 1회용 code → complete.
// 콜백만으로 끝내지 않는 이유: 남이 시작한 인가 주소를 내가 승인하면 내 login이 남의 프로필에 인증됨으로 붙는다(OAuth 계정 연결 CSRF). complete의 Bearer가 시작한 사람과 같아야 한다
@Service
class GitHubContactLinkService(
    private val github: GitHubAccounts?, // api 모듈에만 구현이 있다. 없으면(어드민) 503
    private val links: GitHubContactLinkRepository,
    private val profiles: ProfileService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun start(userId: UserId, platform: LinkPlatform): String {
        val github = requireConfigured()
        profiles.requireConsent(userId)
        return github.authorizeUrl(links.issue(GitHubLinkStep.START, GitHubContactLink(userId, platform)))
    }

    // GitHub이 사용자 브라우저를 돌려보낸 결과. 브라우저가 보낸 요청이라 실패도 예외 대신 앱·웹 주소(error=cancelled|failed)로 알린다(설정이 없을 때만 503). 토큰·login은 주소에 싣지 않는다
    fun callback(state: String?, code: String?, error: String?): URI {
        val github = requireConfigured()
        // state를 읽지 못하면 어느 플랫폼이 시작했는지 몰라 앱 주소로 보낸다
        val started = state?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let { links.consume(it, GitHubLinkStep.START) }
            ?: return github.redirect(LinkPlatform.IOS, "error", "failed").also { log.info("GitHub callback without a live state") }
        if (error != null || code.isNullOrBlank()) {
            return github.redirect(started.platform, "error", if (error == "access_denied") "cancelled" else "failed")
        }
        val login = github.login(code)?.takeIf { LOGIN.matches(it) }
            ?: return github.redirect(started.platform, "error", "failed").also { log.info("GitHub login lookup failed") }
        return github.redirect(started.platform, "code", links.issue(GitHubLinkStep.RESULT, started.copy(login = login)).toString())
    }

    // 결과 code를 시작한 사람만 바꿔 간다. 없거나 만료됐거나 다른 사람 것이면 모두 같은 400 GITHUB_LINK_EXPIRED
    fun complete(userId: UserId, code: UUID): Profile {
        requireConfigured()
        val result = links.consume(code, GitHubLinkStep.RESULT)?.takeIf { it.userId == userId }
            ?: throw InvalidInputException("GitHub 연결이 만료되었어요. 다시 시도해 주세요", "GitHub link result missing or not owned: $code", code = GITHUB_LINK_EXPIRED)
        return profiles.markContactVerified(userId, ContactKind.GITHUB, result.login!!)
    }

    private fun requireConfigured(): GitHubAccounts =
        github?.takeIf { it.isConfigured() } ?: throw ServiceUnavailableException("GitHub 연결을 지금 쓸 수 없습니다", "dearby.github not configured")

    companion object {
        const val GITHUB_LINK_EXPIRED = "GITHUB_LINK_EXPIRED"
        // GitHub 사용자명: 영문·숫자·하이픈 39자까지
        private val LOGIN = Regex("^[A-Za-z0-9-]{1,39}$")
    }
}
