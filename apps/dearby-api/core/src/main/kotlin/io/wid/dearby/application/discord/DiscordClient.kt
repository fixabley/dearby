package io.wid.dearby.application.discord

import io.wid.dearby.application.profile.LinkPlatform
import java.net.URI
import java.time.Duration
import java.util.*

// 디스코드 HTTP 쪽 포트(native-v1.md "디스코드 연결 구현 기록 — 2026-11-05"). 구현은 api 모듈 adaptor/discord. 어드민에는 구현이 없어 서비스가 null로 받는다
interface DiscordClient {

    // 사용자 브라우저가 열 인가 주소. ACCOUNT: scope identify guilds.join, GUILD: scope bot + permissions 268435457
    fun authorizeUrl(flow: DiscordFlow, state: UUID): String

    // 콜백이 결과(code 또는 error)를 붙여 앱·웹으로 돌려보낼 주소. 두 흐름 모두 같은 주소에 flow=account|guild를 붙인다
    fun redirect(platform: LinkPlatform, flow: DiscordFlow, name: String, value: String): URI

    // 인가 code를 토큰으로 바꾼다(redirect_uri는 그 흐름의 콜백). GUILD 흐름이면 응답의 guild.id를 담는다. 실패하면 null
    fun exchangeCode(flow: DiscordFlow, code: String): DiscordTokenGrant?

    // 리프레시 토큰으로 새 쌍을 받는다(디스코드는 리프레시 토큰도 바꿔 준다). 거절(invalid_grant)이나 실패면 null
    fun refresh(refreshToken: String): DiscordTokenGrant?

    // 토큰 폐기(연결 해제·중복 연결). 실패해도 막지 않는다
    fun revoke(token: String)

    // 사용자 액세스 토큰으로 GET /users/@me
    fun currentUser(accessToken: String): DiscordUser?

    // 봇 토큰으로 GET /guilds/{id}(이름·역할 포함). 봇이 그 서버에 없으면 BOT_NOT_IN_GUILD
    fun guild(guildId: String): DiscordResult<DiscordGuild>

    // 봇 자신의 그 서버 멤버 역할 id
    fun botRoleIds(guildId: String): DiscordResult<Set<String>>

    // PUT /guilds/{g}/members/{u}(봇 토큰 + 사용자 액세스 토큰 guilds.join, roles=[roleId]). true = 새로 넣음(역할 함께), false = 이미 멤버(역할은 적용되지 않음)
    fun addMember(guildId: String, discordUserId: String, accessToken: String, roleId: String): DiscordResult<Boolean>

    // PUT /guilds/{g}/members/{u}/roles/{r}
    fun addRole(guildId: String, discordUserId: String, roleId: String): DiscordResult<Unit>

    // DELETE /guilds/{g}/members/{u}/roles/{r}. 서버에서 내보내지는 않는다
    fun removeRole(guildId: String, discordUserId: String, roleId: String): DiscordResult<Unit>
}

enum class DiscordFlow { ACCOUNT, GUILD }

data class DiscordTokenGrant(val accessToken: String, val refreshToken: String, val expiresIn: Duration, val scope: String, val guildId: String? = null)

data class DiscordUser(val id: String, val username: String)

data class DiscordGuild(val id: String, val name: String, val roles: List<DiscordRole>)

data class DiscordRole(val id: String, val name: String, val position: Int, val permissions: Long, val managed: Boolean)

sealed interface DiscordResult<out T> {
    data class Ok<T>(val value: T) : DiscordResult<T>
    data class Failed(val failure: DiscordFailure) : DiscordResult<Nothing>
}

// 디스코드 호출 실패의 종류. 앞으로 직접 개설 모임이 BROKEN 상태·재시도 큐를 정하는 데 쓴다
enum class DiscordFailure {
    NOT_CONFIGURED, // DISCORD_* 설정이 없다
    ACCOUNT_NOT_LINKED, // 그 사용자가 디스코드를 연결하지 않았다
    GUILD_CONNECTION_MISSING, // 서버 연결 행이 없다
    USER_TOKEN_INVALID, // 사용자 토큰이 만료·폐기됐다(다시 연결 필요)
    BOT_NOT_IN_GUILD, // 봇이 서버에서 빠졌다(404 Unknown Guild, 403 Missing Access)
    MISSING_PERMISSIONS, // 봇 권한이 없거나 역할 순서가 봇보다 높다(403 Missing Permissions)
    UNKNOWN_ROLE, // 역할이 지워졌다
    UNKNOWN_MEMBER, // 사용자가 서버에 없다
    RATE_LIMITED, // 429
    UNAVAILABLE, // 네트워크·5xx·그 밖
}
