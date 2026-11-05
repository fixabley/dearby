package io.wid.dearby.application.discord

import io.wid.dearby.application.profile.LinkPlatform
import io.wid.dearby.domain.ConflictException
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.ServiceUnavailableException
import io.wid.dearby.domain.UserId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

// 디스코드 계정 연결(native-v1.md "디스코드 연결 구현 기록 — 2026-11-05"). 로그인 수단이 아니다(user_identities·LAST_SIGN_IN_METHOD와 무관).
// GitHub 연락처 연결과 같은 start → 콜백 → 1회용 code → complete(Bearer). 콜백만으로 끝내면 남이 시작한 인가 주소를 내가 승인했을 때
// 내 디스코드(guilds.join 토큰 포함)가 남의 계정에 붙는다. 디스코드 code의 토큰 교환은 complete에서 한다
@Service
class DiscordAccountService(
    private val discord: DiscordClient?, // api 모듈에만 구현이 있다. 없으면(어드민) 503
    private val properties: DiscordProperties,
    private val states: DiscordOAuthStateRepository,
    private val accounts: DiscordAccountRepository,
    private val cipher: DiscordTokenCipher,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun isAvailable(): Boolean = discord != null && properties.isConfigured()

    // GET /v1/me의 connections.discord. 설정이 없으면 available=false(앱은 행을 숨긴다)
    fun status(userId: UserId): DiscordConnectionStatus =
        if (!isAvailable()) DiscordConnectionStatus(false, null)
        else DiscordConnectionStatus(true, accounts.find(userId)?.let { LinkedDiscord(it.discordUserId, it.username, it.linkedAt) })

    fun start(userId: UserId, platform: LinkPlatform): String {
        val discord = requireConfigured()
        return discord.authorizeUrl(DiscordFlow.ACCOUNT, states.issue(DiscordOAuthState(userId, DiscordFlow.ACCOUNT, DiscordOAuthStep.START, platform)))
    }

    // 브라우저가 보낸 요청이라 실패도 예외 대신 앱·웹 주소(error=cancelled|failed)로 알린다(설정이 없을 때만 503). 토큰·사용자명은 주소에 싣지 않는다
    fun callback(state: String?, code: String?, error: String?): URI =
        DiscordCallbacks.handle(requireConfigured(), states, DiscordFlow.ACCOUNT, state, code, error)

    // 결과 code를 시작한 사람만 바꿔 간다. 없거나 만료됐거나 남의 것이면 400 DISCORD_LINK_EXPIRED.
    // 다른 Dearby 계정에 이미 연결된 디스코드면 409 DISCORD_ALREADY_LINKED(애플·구글 연결과 같은 규칙). 다시 연결하면 새 토큰으로 바꾼다
    fun complete(userId: UserId, code: UUID): LinkedDiscord {
        val discord = requireConfigured()
        val result = states.consume(code, DiscordFlow.ACCOUNT, DiscordOAuthStep.RESULT)?.takeIf { it.userId == userId }
            ?: throw InvalidInputException("디스코드 연결이 만료되었어요. 다시 시도해 주세요", "Discord link result missing or not owned: $code", code = DISCORD_LINK_EXPIRED)
        val grant = discord.exchangeCode(DiscordFlow.ACCOUNT, result.discordCode!!)
            ?: throw InvalidInputException("디스코드 연결이 만료되었어요. 다시 시도해 주세요", "Discord code exchange failed", code = DISCORD_LINK_EXPIRED)
        if (REQUIRED_SCOPES.any { it !in grant.scope.split(' ') }) {
            discord.revoke(grant.refreshToken)
            throw InvalidInputException("디스코드 권한을 모두 허용해 주세요", "Discord grant missing scopes", code = DISCORD_LINK_EXPIRED)
        }
        val user = discord.currentUser(grant.accessToken)
            ?: throw ServiceUnavailableException("디스코드에 지금 연결할 수 없습니다", "Discord /users/@me failed")
        val owner = accounts.findOwner(user.id)
        if (owner != null && owner != userId) {
            discord.revoke(grant.refreshToken)
            throw ConflictException("이미 다른 계정에 연결된 디스코드예요", "Discord ${user.id} already linked to another user", code = DISCORD_ALREADY_LINKED)
        }
        // 같은 사용자가 다른 디스코드로 바꾸면 옛 토큰을 폐기한다
        accounts.find(userId)?.takeIf { it.discordUserId != user.id }?.let { revokeStored(it) }
        val now = now()
        accounts.save(
            StoredDiscordAccount(
                userId, user.id, user.username,
                cipher.seal(grant.accessToken, userId), cipher.seal(grant.refreshToken, userId),
                now.plus(grant.expiresIn), now,
            )
        )
        return LinkedDiscord(user.id, user.username, now)
    }

    // 연결 해제: 디스코드에 리프레시 토큰 폐기를 요청하고(실패해도 지운다) 행을 지운다. 직접 개설 참여의 역할 회수는 참여 기능이 생길 때 붙인다
    @Transactional
    fun unlink(userId: UserId) {
        requireConfigured()
        val stored = accounts.find(userId) ?: throw NotFoundException("연결된 디스코드 계정이 없습니다", "No Discord account for user $userId")
        revokeStored(stored)
        accounts.delete(userId)
    }

    // 서버 추가에 쓸 유효한 액세스 토큰. 1분 안에 만료되면 갱신해 저장한다(행을 잠가 동시 갱신을 막는다). 다시 연결이 필요하면 Failed(USER_TOKEN_INVALID)
    @Transactional
    fun freshAccessToken(userId: UserId): DiscordResult<FreshToken> {
        val discord = discord ?: return DiscordResult.Failed(DiscordFailure.NOT_CONFIGURED)
        val stored = accounts.findForUpdate(userId) ?: return DiscordResult.Failed(DiscordFailure.ACCOUNT_NOT_LINKED)
        val now = now()
        if (stored.tokenExpiresAt.isAfter(now.plus(REFRESH_MARGIN))) {
            return cipher.unseal(stored.accessToken, userId)?.let { DiscordResult.Ok(FreshToken(stored.discordUserId, it)) }
                ?: DiscordResult.Failed(DiscordFailure.USER_TOKEN_INVALID)
        }
        val refreshToken = cipher.unseal(stored.refreshToken, userId) ?: return DiscordResult.Failed(DiscordFailure.USER_TOKEN_INVALID)
        val grant = discord.refresh(refreshToken)
            ?: return DiscordResult.Failed(DiscordFailure.USER_TOKEN_INVALID).also { log.info("Discord token refresh rejected for user {}", userId) }
        accounts.updateTokens(userId, cipher.seal(grant.accessToken, userId), cipher.seal(grant.refreshToken, userId), now.plus(grant.expiresIn))
        return DiscordResult.Ok(FreshToken(stored.discordUserId, grant.accessToken))
    }

    private fun revokeStored(stored: StoredDiscordAccount) {
        cipher.unseal(stored.refreshToken, stored.userId)?.let { discord?.revoke(it) }
    }

    private fun requireConfigured(): DiscordClient =
        discord?.takeIf { properties.isConfigured() } ?: throw ServiceUnavailableException("디스코드 연결을 지금 쓸 수 없습니다", "dearby.discord not configured")

    data class FreshToken(val discordUserId: String, val accessToken: String)

    companion object {
        const val DISCORD_LINK_EXPIRED = "DISCORD_LINK_EXPIRED"
        const val DISCORD_ALREADY_LINKED = "DISCORD_ALREADY_LINKED"
        private val REQUIRED_SCOPES = listOf("identify", "guilds.join")
        private val REFRESH_MARGIN = Duration.ofMinutes(1)
        private fun now() = OffsetDateTime.now(ZoneOffset.UTC)
    }
}

data class DiscordConnectionStatus(val available: Boolean, val account: LinkedDiscord?)

data class LinkedDiscord(val discordUserId: String, val username: String, val linkedAt: OffsetDateTime)

// 두 흐름(계정·봇 서버)이 같이 쓰는 콜백 처리: state를 확인하고 디스코드 code를 RESULT로 옮겨 1회용 code를 앱·웹에 돌려준다
internal object DiscordCallbacks {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(discord: DiscordClient, states: DiscordOAuthStateRepository, flow: DiscordFlow, state: String?, code: String?, error: String?): URI {
        // state를 읽지 못하면 어느 플랫폼이 시작했는지 몰라 앱 주소로 보낸다
        val started = state?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let { states.consume(it, flow, DiscordOAuthStep.START) }
            ?: return discord.redirect(LinkPlatform.IOS, flow, "error", "failed").also { log.info("Discord {} callback without a live state", flow) }
        if (error != null || code.isNullOrBlank() || code.length > MAX_CODE) {
            return discord.redirect(started.platform, flow, "error", if (error == "access_denied") "cancelled" else "failed")
        }
        val result = states.issue(started.copy(step = DiscordOAuthStep.RESULT, discordCode = code))
        return discord.redirect(started.platform, flow, "code", result.toString())
    }

    private const val MAX_CODE = 200
}
