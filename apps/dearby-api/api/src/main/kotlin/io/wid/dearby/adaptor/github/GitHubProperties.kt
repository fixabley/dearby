package io.wid.dearby.adaptor.github

import org.springframework.boot.context.properties.ConfigurationProperties

// GitHub 연락처 연결(native-v1.md "연락처 인증"). clientId·clientSecret·callbackUrl 중 하나라도 비면 /v1/me/contacts/github/*는 503(이 설정이 없어도 기동한다)
@ConfigurationProperties("dearby.github")
data class GitHubProperties(
    val clientId: String? = null, // GitHub OAuth App의 Client ID
    val clientSecret: String? = null, // 같은 OAuth App의 Client secret. 저장소·채팅에 넣지 않는다
    val callbackUrl: String? = null, // OAuth App에 등록한 Authorization callback URL(GET /v1/me/contacts/github/callback)
    val appRedirectUri: String = "dearby://contacts/github", // iOS·Android: 콜백이 결과 code 또는 error를 붙여 돌려보내는 주소
    val webRedirectUri: String = "https://dearby.wid.io.kr/profile", // 웹: 같은 결과를 붙여 돌려보내는 주소
)
