package io.wid.dearby.adaptor.web.v1.me

import io.wid.dearby.application.discord.DiscordAccountService
import io.wid.dearby.application.discord.DiscordGuildConnection
import io.wid.dearby.application.discord.DiscordGuildService
import io.wid.dearby.application.discord.GuildRole
import io.wid.dearby.application.discord.GuildWithRoles
import io.wid.dearby.application.discord.LinkedDiscord
import io.wid.dearby.application.profile.LinkPlatform
import io.wid.dearby.domain.InvalidInputException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.time.OffsetDateTime
import java.util.*

// 디스코드 계정 연결과 봇 서버 연결(native-v1.md "디스코드 연결 구현 기록 — 2026-11-05"). 설정이 없으면 모두 503.
// 콜백 둘만 인증 없이 받고(ApiAccessRules) 나머지는 로그인 사용자 자신의 것
@RestController
class DiscordController(private val accounts: DiscordAccountService, private val guilds: DiscordGuildService) {

    @PostMapping("/v1/me/connections/discord/start")
    fun startAccount(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: StartRequest) =
        StartResponse(accounts.start(userId(jwt), platform(request)))

    // 디스코드가 사용자 브라우저를 돌려보낸다. 결과(1회용 code 또는 error)를 붙여 앱·웹으로 303
    @GetMapping("/v1/me/connections/discord/callback")
    fun accountCallback(
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.SEE_OTHER).location(accounts.callback(state, code, error)).build()

    @PostMapping("/v1/me/connections/discord/complete")
    fun completeAccount(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: CompleteRequest) =
        AccountResponse.from(accounts.complete(userId(jwt), request.code))

    @DeleteMapping("/v1/me/connections/discord")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlinkAccount(@AuthenticationPrincipal jwt: Jwt) = accounts.unlink(userId(jwt))

    // 봇 초대 주소(scope=bot, permissions=268435457)
    @PostMapping("/v1/me/discord/guilds/start")
    fun startGuild(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: StartRequest) =
        StartResponse(guilds.start(userId(jwt), platform(request)))

    // 디스코드가 봇 승인 뒤 브라우저를 돌려보낸다(code·guild_id·permissions·state). guild_id는 참고값이라 읽지 않는다
    @GetMapping("/v1/me/discord/guilds/callback")
    fun guildCallback(
        @RequestParam(required = false) state: String?,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) error: String?,
    ): ResponseEntity<Void> = ResponseEntity.status(HttpStatus.SEE_OTHER).location(guilds.callback(state, code, error)).build()

    @PostMapping("/v1/me/discord/guilds/complete")
    @ResponseStatus(HttpStatus.CREATED)
    fun completeGuild(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: CompleteRequest) =
        GuildRolesResponse.from(guilds.complete(userId(jwt), request.code))

    @GetMapping("/v1/me/discord/guilds")
    fun listGuilds(@AuthenticationPrincipal jwt: Jwt) = GuildListResponse(guilds.list(userId(jwt)).map(GuildResponse::from))

    @GetMapping("/v1/me/discord/guilds/{id}/roles")
    fun roles(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID) = GuildRolesResponse.from(guilds.roles(userId(jwt), id))

    @DeleteMapping("/v1/me/discord/guilds/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteGuild(@AuthenticationPrincipal jwt: Jwt, @PathVariable id: UUID) = guilds.delete(userId(jwt), id)

    data class StartRequest(val platform: String)

    data class StartResponse(val authorizeUrl: String)

    data class CompleteRequest(val code: UUID)

    data class AccountResponse(val discordUserId: String, val username: String, val linkedAt: OffsetDateTime) {
        companion object {
            fun from(it: LinkedDiscord) = AccountResponse(it.discordUserId, it.username, it.linkedAt)
        }
    }

    data class GuildResponse(val id: UUID, val guildId: String, val guildName: String, val createdAt: OffsetDateTime) {
        companion object {
            fun from(it: DiscordGuildConnection) = GuildResponse(it.id, it.guildId, it.guildName, it.createdAt)
        }
    }

    data class GuildListResponse(val items: List<GuildResponse>)

    data class GuildRolesResponse(val id: UUID, val guildId: String, val guildName: String, val roles: List<GuildRole>) {
        companion object {
            fun from(it: GuildWithRoles) = GuildRolesResponse(it.connection.id, it.connection.guildId, it.connection.guildName, it.roles)
        }
    }

    private fun platform(request: StartRequest) = LinkPlatform.entries.firstOrNull { it.name.equals(request.platform, ignoreCase = true) }
        ?: throw InvalidInputException("platform은 ios·android·web 중 하나입니다")

    private fun userId(jwt: Jwt): UUID = UUID.fromString(jwt.subject)
}
