package io.wid.dearby.auth.security

import io.wid.dearby.application.auth.GoogleTokenClient
import io.wid.dearby.domain.AuthenticationFailedException
import io.wid.dearby.domain.ServiceUnavailableException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.JsonNode

// 구글 토큰 엔드포인트: 인가 code를 client secret으로 바꿔 ID 토큰만 쓴다. refresh token은 요청하지 않는다
@Component
class RestGoogleTokenClient(private val properties: GoogleProperties, builder: RestClient.Builder) : GoogleTokenClient {

    // admin 앱에는 RestClient.Builder 빈이 없어 직접 만든다. 테스트는 MockRestServiceServer를 붙인 builder를 넘긴다
    @Autowired
    constructor(properties: GoogleProperties) : this(properties, RestClient.builder())

    private val http = builder.baseUrl(TOKEN_URL).build()

    override fun exchange(code: String): String {
        if (!properties.isConfigured()) throw ServiceUnavailableException("구글 로그인을 지금 쓸 수 없습니다", "dearby.google settings missing")
        val form = LinkedMultiValueMap<String, String>().apply {
            add("code", code)
            add("client_id", properties.clientId)
            add("client_secret", properties.clientSecret)
            add("redirect_uri", properties.callbackUrl)
            add("grant_type", "authorization_code")
        }
        val idToken = try {
            http.post().contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(JsonNode::class.java)?.path("id_token")?.asString(null)
        } catch (e: RestClientException) {
            throw AuthenticationFailedException("구글 로그인을 확인하지 못했습니다", "Google token exchange failed: ${e.message}")
        }
        return idToken?.takeIf { it.isNotBlank() }
            ?: throw AuthenticationFailedException("구글 로그인을 확인하지 못했습니다", "Google token response without id_token")
    }

    private companion object {
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    }
}
