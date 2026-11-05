package io.wid.dearby.application.discord

import io.wid.dearby.domain.UserId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.*

// 참여자를 디스코드 서버에 넣고 역할을 주거나 거둔다(native-v1.md "디스코드 연결(2번안)" 참여·역할 회수). 아직 공개 API가 없고 직접 개설 참여가 부른다.
// 예외 대신 DiscordGrantResult로 실패 종류를 돌려준다: 디스코드 실패가 참여를 막지 않고 호출 쪽이 BROKEN 표시·재시도 큐를 정한다
@Service
class DiscordMembershipService(
    private val discord: DiscordClient?,
    private val properties: DiscordProperties,
    private val accounts: DiscordAccountService,
    private val guilds: DiscordGuildConnectionRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 서버에 없으면 PUT /guilds/{g}/members/{u}로 roles=[roleId]와 함께 넣고(201), 이미 있으면(204, 역할 적용 안 됨) 역할만 PUT
    fun grantMembership(userId: UserId, guildConnectionId: UUID, roleId: String): DiscordGrantResult {
        val discord = discord?.takeIf { properties.isConfigured() } ?: return failed(DiscordFailure.NOT_CONFIGURED)
        val connection = guilds.find(guildConnectionId) ?: return failed(DiscordFailure.GUILD_CONNECTION_MISSING)
        val token = when (val t = accounts.freshAccessToken(userId)) {
            is DiscordResult.Ok -> t.value
            is DiscordResult.Failed -> return failed(t.failure)
        }
        val added = when (val r = discord.addMember(connection.guildId, token.discordUserId, token.accessToken, roleId)) {
            is DiscordResult.Ok -> r.value
            is DiscordResult.Failed -> return failed(r.failure).also { log(userId, connection, "add member", r.failure) }
        }
        if (added) return DiscordGrantResult.Granted(addedToGuild = true)
        return when (val r = discord.addRole(connection.guildId, token.discordUserId, roleId)) {
            is DiscordResult.Ok -> DiscordGrantResult.Granted(addedToGuild = false)
            is DiscordResult.Failed -> failed(r.failure).also { log(userId, connection, "add role", r.failure) }
        }
    }

    // 역할만 거둔다(서버에서 내보내지 않는다). 사용자가 이미 서버를 나갔으면(UNKNOWN_MEMBER) 거둘 것이 없으니 성공으로 본다.
    // 사용자 토큰이 필요 없어 디스코드 연결을 해제한 뒤에도 저장해 둔 디스코드 id로 부를 수 있게 discordUserId를 따로 받는다
    fun revoke(discordUserId: String, guildConnectionId: UUID, roleId: String): DiscordGrantResult {
        val discord = discord?.takeIf { properties.isConfigured() } ?: return failed(DiscordFailure.NOT_CONFIGURED)
        val connection = guilds.find(guildConnectionId) ?: return failed(DiscordFailure.GUILD_CONNECTION_MISSING)
        return when (val r = discord.removeRole(connection.guildId, discordUserId, roleId)) {
            is DiscordResult.Ok -> DiscordGrantResult.Revoked
            is DiscordResult.Failed ->
                if (r.failure == DiscordFailure.UNKNOWN_MEMBER) DiscordGrantResult.Revoked
                else failed(r.failure).also { log.info("Discord remove role failed for guild connection {}: {}", connection.id, r.failure) }
        }
    }

    private fun log(userId: UserId, connection: DiscordGuildConnection, step: String, failure: DiscordFailure) =
        log.info("Discord {} failed for user {} guild connection {}: {}", step, userId, connection.id, failure)

    private fun failed(failure: DiscordFailure) = DiscordGrantResult.Failed(failure)
}

sealed interface DiscordGrantResult {
    data class Granted(val addedToGuild: Boolean) : DiscordGrantResult
    data object Revoked : DiscordGrantResult
    data class Failed(val failure: DiscordFailure) : DiscordGrantResult
}
