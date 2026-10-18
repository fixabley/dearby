package io.wid.dearby.auth.security

import org.springframework.boot.context.properties.ConfigurationProperties

// 구글 로그인 설정(native-v1.md "구글 로그인"). 셋 중 하나라도 비어 있으면 /v1/auth/google/* 는 503(이 설정이 없어도 기동한다)
@ConfigurationProperties("dearby.google")
data class GoogleProperties(
    val clientId: String? = null, // OAuth 클라이언트(종류 "웹 애플리케이션") ID. ID 토큰의 aud
    val clientSecret: String? = null, // 그 클라이언트의 시크릿(서버에만 둔다)
    val callbackUrl: String? = null, // 승인된 리디렉션 URI(GET /v1/auth/google/callback)
    val appRedirectUri: String = "dearby://auth/google", // 콜백이 결과 코드를 붙여 앱으로 돌려보내는 주소
    val jwkSetUri: String = "https://www.googleapis.com/oauth2/v3/certs",
) {
    fun isConfigured() = !clientId.isNullOrBlank() && !clientSecret.isNullOrBlank() && !callbackUrl.isNullOrBlank()
}
