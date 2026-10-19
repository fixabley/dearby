package io.wid.dearby.auth.security

import org.springframework.boot.context.properties.ConfigurationProperties

// 구글 로그인 설정(native-v1.md "구글 로그인 — 네이티브"). 허용 client id가 하나도 없으면 /v1/auth/google/* 는 503(이 설정이 없어도 기동한다)
@ConfigurationProperties("dearby.google")
data class GoogleProperties(
    // ID 토큰의 aud로 허용하는 OAuth 클라이언트 id(쉼표 구분). Android는 웹 클라이언트 id(serverClientId), iOS SDK는 iOS 클라이언트 id를 aud로 보낸다
    val clientIds: String? = null,
    val jwkSetUri: String = "https://www.googleapis.com/oauth2/v3/certs",
) {
    val allowedClientIds: List<String> = clientIds.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)

    fun isConfigured() = allowedClientIds.isNotEmpty()
}
