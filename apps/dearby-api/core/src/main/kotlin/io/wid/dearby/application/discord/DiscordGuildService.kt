package io.wid.dearby.application.discord

import io.wid.dearby.application.profile.LinkPlatform
import io.wid.dearby.domain.ConflictException
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.ServiceUnavailableException
import io.wid.dearby.domain.UserId
import org.springframework.stereotype.Service
import java.net.URI
import java.util.*

// 봇 서버 연결(native-v1.md "디스코드 연결 구현 기록 — 2026-11-05"). 앞으로 직접 개설자가 모임에 디스코드 서버를 붙일 때 쓴다.
// 계정 연결과 같은 start → 콜백 → 1회용 code → complete(Bearer). 콜백의 guild_id는 참고값이라 믿지 않는다:
// complete에서 디스코드 code를 토큰으로 바꿔 응답의 guild.id(실제로 봇을 승인한 서버)를 읽고, 봇 토큰으로 그 서버에 봇이 있는지 다시 확인한다.
// code 교환 없이 guild_id만 믿으면 봇이 이미 들어간 남의 서버 id를 넣어 연결을 가로챌 수 있다
@Service
class DiscordGuildService(
    private val discord: DiscordClient?,
    private val properties: DiscordProperties,
    private val states: DiscordOAuthStateRepository,
    private val guilds: DiscordGuildConnectionRepository,
) {

    fun start(userId: UserId, platform: LinkPlatform): String {
        val discord = requireConfigured()
        return discord.authorizeUrl(DiscordFlow.GUILD, states.issue(DiscordOAuthState(userId, DiscordFlow.GUILD, DiscordOAuthStep.START, platform)))
    }

    fun callback(state: String?, code: String?, error: String?): URI =
        DiscordCallbacks.handle(requireConfigured(), states, DiscordFlow.GUILD, state, code, error)

    // 없거나 만료됐거나 남의 결과면 400 DISCORD_LINK_EXPIRED. 봇이 서버에 없으면 409 DISCORD_BOT_NOT_IN_GUILD
    fun complete(userId: UserId, code: UUID): GuildWithRoles {
        val discord = requireConfigured()
        val result = states.consume(code, DiscordFlow.GUILD, DiscordOAuthStep.RESULT)?.takeIf { it.userId == userId }
            ?: throw InvalidInputException("디스코드 서버 연결이 만료되었어요. 다시 시도해 주세요", "Discord guild result missing or not owned: $code", code = DiscordAccountService.DISCORD_LINK_EXPIRED)
        val grant = discord.exchangeCode(DiscordFlow.GUILD, result.discordCode!!)
        val guildId = grant?.guildId
            ?: throw InvalidInputException("디스코드 서버 연결이 만료되었어요. 다시 시도해 주세요", "Discord bot code exchange failed", code = DiscordAccountService.DISCORD_LINK_EXPIRED)
        // bot 범위 토큰은 저장하지 않고 버린다. 폐기 요청은 하지 않는다(그 승인을 거두면 봇이 서버에서 빠질 수 있어 확인 전까지 피함)
        val guild = load(discord, guildId)
        val connection = guilds.upsert(userId, guild.id, guild.name.take(MAX_NAME))
        return GuildWithRoles(connection, roles(discord, guild))
    }

    fun list(userId: UserId): List<DiscordGuildConnection> {
        requireConfigured()
        return guilds.findAllByUser(userId)
    }

    // 연결한 서버의 역할 목록과 봇이 줄 수 있는지. 디스코드에서 서버 이름이 바뀌었으면 저장한 이름도 맞춘다
    fun roles(userId: UserId, connectionId: UUID): GuildWithRoles {
        val discord = requireConfigured()
        val connection = owned(userId, connectionId)
        val guild = load(discord, connection.guildId)
        val updated = if (guild.name.take(MAX_NAME) != connection.guildName) guilds.upsert(userId, guild.id, guild.name.take(MAX_NAME)) else connection
        return GuildWithRoles(updated, roles(discord, guild))
    }

    // 연결 행만 지운다(봇을 서버에서 내보내지는 않는다. 서버 관리자가 디스코드에서 직접 뺀다)
    fun delete(userId: UserId, connectionId: UUID) {
        requireConfigured()
        if (!guilds.delete(userId, connectionId)) throw notFound(userId, connectionId)
    }

    private fun owned(userId: UserId, id: UUID) = guilds.find(id)?.takeIf { it.userId == userId } ?: throw notFound(userId, id)

    private fun load(discord: DiscordClient, guildId: String): DiscordGuild = when (val r = discord.guild(guildId)) {
        is DiscordResult.Ok -> r.value
        is DiscordResult.Failed -> throw failure(r.failure, guildId)
    }

    // 디스코드 규칙: 봇은 자기 가장 높은 역할보다 낮은 역할만 줄 수 있고 MANAGE_ROLES(또는 ADMINISTRATOR)가 있어야 한다.
    // 연동 앱이 관리하는 역할(managed)과 @everyone(id = 서버 id)은 줄 수 없다. 같은 position의 순서(id 비교)는 보지 않는다
    private fun roles(discord: DiscordClient, guild: DiscordGuild): List<GuildRole> {
        val botRoleIds = when (val r = discord.botRoleIds(guild.id)) {
            is DiscordResult.Ok -> r.value
            is DiscordResult.Failed -> throw failure(r.failure, guild.id)
        }
        val everyone = guild.roles.firstOrNull { it.id == guild.id }
        val botRoles = guild.roles.filter { it.id in botRoleIds }
        val permissions = botRoles.fold(everyone?.permissions ?: 0L) { acc, role -> acc or role.permissions }
        val canManage = permissions and (ADMINISTRATOR or MANAGE_ROLES) != 0L
        val top = botRoles.maxOfOrNull { it.position } ?: 0
        return guild.roles.filter { it.id != guild.id }.sortedByDescending { it.position }.map {
            GuildRole(it.id, it.name, it.position, canManage && !it.managed && it.position < top)
        }
    }

    private fun failure(failure: DiscordFailure, guildId: String): RuntimeException = when (failure) {
        DiscordFailure.BOT_NOT_IN_GUILD, DiscordFailure.MISSING_PERMISSIONS ->
            ConflictException("디스코드 서버에 Dearby 봇이 없어요. 다시 연결해 주세요", "Discord bot not in guild $guildId ($failure)", code = DISCORD_BOT_NOT_IN_GUILD)
        else -> ServiceUnavailableException("디스코드에 지금 연결할 수 없습니다", "Discord guild $guildId lookup failed: $failure")
    }

    private fun notFound(userId: UserId, id: UUID) = NotFoundException("디스코드 서버 연결을 찾을 수 없습니다", "Discord guild connection $id not owned by $userId")

    private fun requireConfigured(): DiscordClient =
        discord?.takeIf { properties.isConfigured() } ?: throw ServiceUnavailableException("디스코드 연결을 지금 쓸 수 없습니다", "dearby.discord not configured")

    companion object {
        const val DISCORD_BOT_NOT_IN_GUILD = "DISCORD_BOT_NOT_IN_GUILD"
        // 봇 초대 권한 = CREATE_INSTANT_INVITE(1<<0) + MANAGE_ROLES(1<<28) = 268435457(native-v1.md "디스코드 연결(2번안)")
        const val BOT_PERMISSIONS = (1L shl 0) or (1L shl 28)
        private const val ADMINISTRATOR = 1L shl 3
        private const val MANAGE_ROLES = 1L shl 28
        private const val MAX_NAME = 100
    }
}

data class GuildWithRoles(val connection: DiscordGuildConnection, val roles: List<GuildRole>)

data class GuildRole(val id: String, val name: String, val position: Int, val assignable: Boolean)
