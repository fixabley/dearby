package io.wid.dearby.adaptor.github

import io.wid.dearby.application.profile.GitHubAccounts
import io.wid.dearby.application.profile.LinkPlatform
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import java.net.URI
import java.util.*

@Configuration
@EnableConfigurationProperties(GitHubProperties::class)
class GitHubConfig

// GitHub OAuth App(Authorization code). 액세스 토큰은 login을 읽는 데만 쓰고 저장하지 않는다. 테스트는 MockRestServiceServer를 붙인 builder를 넘긴다
@Component
class RestGitHubAccounts(private val properties: GitHubProperties, builder: RestClient.Builder) : GitHubAccounts {

    private val log = LoggerFactory.getLogger(javaClass)
    private val http = builder.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT).build()

    override fun isConfigured(): Boolean =
        !properties.clientId.isNullOrBlank() && !properties.clientSecret.isNullOrBlank() && !properties.callbackUrl.isNullOrBlank()

    override fun authorizeUrl(state: UUID): String =
        UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
            .queryParam("client_id", properties.clientId)
            .queryParam("redirect_uri", properties.callbackUrl)
            .queryParam("state", state)
            // scope 없음: 공개 프로필(login)만 읽는다
            .encode().build().toUriString()

    override fun login(code: String): String? = try {
        val form = LinkedMultiValueMap<String, String>().apply {
            add("client_id", properties.clientId)
            add("client_secret", properties.clientSecret)
            add("code", code)
            add("redirect_uri", properties.callbackUrl)
        }
        val token = http.post().uri(TOKEN_URL).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
            .retrieve().body(JsonNode::class.java)?.path("access_token")?.asString(null)
        // GitHub은 code가 틀려도 200에 {"error": …}를 준다
        if (token.isNullOrBlank()) return null.also { log.info("GitHub token exchange returned no access_token") }
        http.get().uri(USER_URL)
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .retrieve().body(JsonNode::class.java)?.path("login")?.asString(null)?.takeIf { it.isNotBlank() }
    } catch (e: RestClientException) {
        log.warn("GitHub login lookup failed: {}", e.message)
        null
    }

    override fun redirect(platform: LinkPlatform, name: String, value: String): URI =
        UriComponentsBuilder.fromUriString(if (platform == LinkPlatform.WEB) properties.webRedirectUri else properties.appRedirectUri)
            .queryParam(name, value).build().toUri()

    private companion object {
        const val AUTHORIZE_URL = "https://github.com/login/oauth/authorize"
        const val TOKEN_URL = "https://github.com/login/oauth/access_token"
        const val USER_URL = "https://api.github.com/user"
        const val API_VERSION = "2022-11-28"
        const val USER_AGENT = "Dearby" // GitHub API는 User-Agent가 없으면 403
    }
}
