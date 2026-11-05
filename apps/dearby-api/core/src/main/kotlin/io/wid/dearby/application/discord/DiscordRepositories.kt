package io.wid.dearby.application.discord

import io.wid.dearby.application.profile.LinkPlatform
import io.wid.dearby.domain.UserId
import java.time.Duration
import java.time.OffsetDateTime
import java.util.*

// 디스코드 OAuth 중간 상태(GitHubContactLinkRepository와 같은 규칙). RESULT는 콜백이 받은 디스코드 인가 code를 complete까지 들고 있는다
interface DiscordOAuthStateRepository {

    fun issue(state: DiscordOAuthState): UUID

    // 유효하면 지우고 돌려준다. 같은 id로 동시에 요청해도 한 번만 성공
    fun consume(id: UUID, flow: DiscordFlow, step: DiscordOAuthStep): DiscordOAuthState?

    companion object {
        val TTL: Duration = Duration.ofMinutes(10)
    }
}

enum class DiscordOAuthStep { START, RESULT }

data class DiscordOAuthState(val userId: UserId, val flow: DiscordFlow, val step: DiscordOAuthStep, val platform: LinkPlatform, val discordCode: String? = null)

// 토큰은 암호문 그대로 다룬다(DiscordTokenCipher가 풀고 잠근다)
interface DiscordAccountRepository {

    fun find(userId: UserId): StoredDiscordAccount?

    // 같은 트랜잭션에서 토큰을 바꾸기 전에 잠근다(리프레시 토큰이 한 번만 쓰여 동시 갱신이 서로를 무효로 만들지 않게)
    fun findForUpdate(userId: UserId): StoredDiscordAccount?

    // 다른 사용자에게 연결된 디스코드 id면 그 사용자 id
    fun findOwner(discordUserId: String): UserId?

    fun save(account: StoredDiscordAccount)

    fun updateTokens(userId: UserId, accessToken: ByteArray, refreshToken: ByteArray, expiresAt: OffsetDateTime)

    fun delete(userId: UserId): Boolean
}

class StoredDiscordAccount(
    val userId: UserId,
    val discordUserId: String,
    val username: String,
    val accessToken: ByteArray,
    val refreshToken: ByteArray,
    val tokenExpiresAt: OffsetDateTime,
    val linkedAt: OffsetDateTime,
)

interface DiscordGuildConnectionRepository {

    // (user_id, guild_id)가 이미 있으면 이름만 바꾸고 그 행을 돌려준다
    fun upsert(userId: UserId, guildId: String, guildName: String): DiscordGuildConnection

    fun find(id: UUID): DiscordGuildConnection?

    fun findAllByUser(userId: UserId): List<DiscordGuildConnection>

    fun delete(userId: UserId, id: UUID): Boolean
}

data class DiscordGuildConnection(val id: UUID, val userId: UserId, val guildId: String, val guildName: String, val createdAt: OffsetDateTime)
