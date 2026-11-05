package io.wid.dearby.adaptor.discord

import io.wid.dearby.application.discord.DiscordClient
import io.wid.dearby.application.discord.DiscordFailure
import io.wid.dearby.application.discord.DiscordFlow
import io.wid.dearby.application.discord.DiscordGuild
import io.wid.dearby.application.discord.DiscordGuildService
import io.wid.dearby.application.discord.DiscordProperties
import io.wid.dearby.application.discord.DiscordResult
import io.wid.dearby.application.discord.DiscordRole
import io.wid.dearby.application.discord.DiscordTokenGrant
import io.wid.dearby.application.discord.DiscordUser
import io.wid.dearby.application.profile.LinkPlatform
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.time.Duration
import java.util.*

// 디스코드 HTTP API v10(native-v1.md "디스코드 연결 구현 기록 — 2026-11-05"). 토큰·응답 본문은 로그에 남기지 않는다(상태 코드와 디스코드 오류 code만).
// 테스트는 MockRestServiceServer를 붙인 builder를 넘긴다
@Component
class RestDiscordClient(
    private val properties: DiscordProperties,
    private val json: ObjectMapper,
    builder: RestClient.Builder,
) : DiscordClient {

    private val log = LoggerFactory.getLogger(javaClass)
    private val http = builder.baseUrl(API).defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build()

    // 봇 자신의 사용자 id(GET /users/@me, 봇 토큰). 한 번 읽으면 바뀌지 않는다
    @Volatile
    private var botUserId: String? = null

    override fun authorizeUrl(flow: DiscordFlow, state: UUID): String {
        val builder = UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
            .queryParam("response_type", "code")
            .queryParam("client_id", properties.clientId)
            .queryParam("state", state)
        when (flow) {
            DiscordFlow.ACCOUNT -> builder.queryParam("scope", "identify guilds.join").queryParam("redirect_uri", properties.callbackUrl)
            // response_type=code + redirect_uri: 돌아온 code를 바꾸면 응답의 guild로 실제 승인한 서버를 안다
            DiscordFlow.GUILD -> builder.queryParam("scope", "bot").queryParam("permissions", DiscordGuildService.BOT_PERMISSIONS)
                .queryParam("redirect_uri", properties.botCallbackUrl)
        }
        return builder.encode().build().toUriString()
    }

    override fun redirect(platform: LinkPlatform, flow: DiscordFlow, name: String, value: String): URI =
        UriComponentsBuilder.fromUriString(if (platform == LinkPlatform.WEB) properties.webRedirectUri else properties.appRedirectUri)
            .queryParam("flow", flow.name.lowercase())
            .queryParam(name, value).build().toUri()

    override fun exchangeCode(flow: DiscordFlow, code: String): DiscordTokenGrant? = token(
        "authorization_code",
        "code" to code,
        "redirect_uri" to (if (flow == DiscordFlow.ACCOUNT) properties.callbackUrl else properties.botCallbackUrl)!!,
    )

    override fun refresh(refreshToken: String): DiscordTokenGrant? = token("refresh_token", "refresh_token" to refreshToken)

    override fun revoke(token: String) {
        try {
            http.post().uri("/oauth2/token/revoke").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(clientForm().apply { add("token", token) }).retrieve().toBodilessEntity()
        } catch (e: RestClientException) {
            log.warn("Discord token revoke failed: {}", status(e))
        }
    }

    override fun currentUser(accessToken: String): DiscordUser? = try {
        http.get().uri("/users/@me").header(HttpHeaders.AUTHORIZATION, "Bearer $accessToken").retrieve().body(JsonNode::class.java)
            ?.let { node -> node.text("id")?.let { id -> DiscordUser(id, node.text("username") ?: id) } }
    } catch (e: RestClientException) {
        log.warn("Discord /users/@me failed: {}", status(e))
        null
    }

    override fun guild(guildId: String): DiscordResult<DiscordGuild> = call("get guild") {
        val node = bot(http.get().uri("/guilds/{id}", guildId)).retrieve().body(JsonNode::class.java)!!
        DiscordGuild(
            id = node.text("id")!!,
            name = node.text("name").orEmpty(),
            roles = node.path("roles").values().map {
                DiscordRole(it.text("id")!!, it.text("name").orEmpty(), it.path("position").asInt(), it.text("permissions")?.toLongOrNull() ?: 0L, it.path("managed").asBoolean())
            },
        )
    }

    override fun botRoleIds(guildId: String): DiscordResult<Set<String>> = call("get bot member") {
        val id = botUserId ?: bot(http.get().uri("/users/@me")).retrieve().body(JsonNode::class.java)!!.text("id")!!.also { botUserId = it }
        bot(http.get().uri("/guilds/{g}/members/{u}", guildId, id)).retrieve().body(JsonNode::class.java)!!
            .path("roles").values().mapNotNull { it.asString(null) }.toSet()
    }

    override fun addMember(guildId: String, discordUserId: String, accessToken: String, roleId: String): DiscordResult<Boolean> = call("add member") {
        val response = bot(http.put().uri("/guilds/{g}/members/{u}", guildId, discordUserId))
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("access_token" to accessToken, "roles" to listOf(roleId)))
            .retrieve().toBodilessEntity()
        // 201 Created = 새로 넣음, 204 No Content = 이미 멤버
        response.statusCode == HttpStatus.CREATED
    }

    override fun addRole(guildId: String, discordUserId: String, roleId: String): DiscordResult<Unit> = call("add role") {
        bot(http.put().uri("/guilds/{g}/members/{u}/roles/{r}", guildId, discordUserId, roleId)).retrieve().toBodilessEntity()
        Unit
    }

    override fun removeRole(guildId: String, discordUserId: String, roleId: String): DiscordResult<Unit> = call("remove role") {
        bot(http.delete().uri("/guilds/{g}/members/{u}/roles/{r}", guildId, discordUserId, roleId)).retrieve().toBodilessEntity()
        Unit
    }

    private fun token(grantType: String, vararg fields: Pair<String, String>): DiscordTokenGrant? = try {
        val form = clientForm().apply {
            add("grant_type", grantType)
            fields.forEach { (k, v) -> add(k, v) }
        }
        val node = http.post().uri("/oauth2/token").contentType(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON)
            .body(form).retrieve().body(JsonNode::class.java)
        node?.text("access_token")?.let { access ->
            DiscordTokenGrant(
                accessToken = access,
                refreshToken = node.text("refresh_token").orEmpty(),
                expiresIn = Duration.ofSeconds(node.path("expires_in").asLong(0)),
                scope = node.text("scope").orEmpty(),
                guildId = node.path("guild").text("id"),
            )
        }
    } catch (e: RestClientException) {
        log.info("Discord token {} failed: {}", grantType, status(e))
        null
    }

    private fun clientForm() = LinkedMultiValueMap<String, String>().apply {
        add("client_id", properties.clientId)
        add("client_secret", properties.clientSecret)
    }

    private fun <S : RestClient.RequestHeadersSpec<S>> bot(spec: S): S = spec.header(HttpHeaders.AUTHORIZATION, "Bot ${properties.botToken}")

    private fun <T> call(what: String, block: () -> T): DiscordResult<T> = try {
        DiscordResult.Ok(block())
    } catch (e: RestClientResponseException) {
        val code = runCatching { json.readTree(e.responseBodyAsString).path("code").asInt(0) }.getOrDefault(0)
        failure(e.statusCode.value(), code).also { log.info("Discord {} failed: status {} code {} -> {}", what, e.statusCode.value(), code, it) }
            .let { DiscordResult.Failed(it) }
    } catch (e: RestClientException) {
        log.warn("Discord {} failed: {}", what, e.javaClass.simpleName)
        DiscordResult.Failed(DiscordFailure.UNAVAILABLE)
    }

    private fun status(e: RestClientException) = (e as? RestClientResponseException)?.statusCode?.value() ?: e.javaClass.simpleName

    private fun JsonNode.text(field: String): String? = path(field).asString(null)?.takeIf { it.isNotBlank() }

    companion object {
        private const val API = "https://discord.com/api/v10"
        private const val AUTHORIZE_URL = "https://discord.com/oauth2/authorize"
        // 디스코드는 봇 요청에 "DiscordBot (url, version)" 모양의 User-Agent를 요구한다
        private const val USER_AGENT = "DiscordBot (https://dearby.wid.io.kr, 1)"

        // 디스코드 JSON 오류 code(https://discord.com/developers/docs/topics/opcodes-and-status-codes#json)
        internal fun failure(status: Int, code: Int): DiscordFailure = when {
            code == 10004 || code == 50001 -> DiscordFailure.BOT_NOT_IN_GUILD // Unknown Guild, Missing Access
            code == 10011 -> DiscordFailure.UNKNOWN_ROLE
            code == 10007 || code == 10013 -> DiscordFailure.UNKNOWN_MEMBER // Unknown Member, Unknown User
            code == 50013 -> DiscordFailure.MISSING_PERMISSIONS // 권한 없음·역할 순서
            code == 50025 -> DiscordFailure.USER_TOKEN_INVALID // Invalid OAuth2 access token
            status == 429 -> DiscordFailure.RATE_LIMITED
            status == 403 -> DiscordFailure.MISSING_PERMISSIONS
            status == 404 -> DiscordFailure.BOT_NOT_IN_GUILD
            else -> DiscordFailure.UNAVAILABLE // 401(봇 토큰 틀림)·5xx 포함
        }
    }
}
