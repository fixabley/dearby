package io.wid.dearby.adaptor.security

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("dearby.webauthn")
data class WebAuthnProperties(
    val rpId: String, // 패스키가 묶이는 도메인. 앱 연동 시 apple-app-site-association·assetlinks.json 도메인과 같아야 함
    val rpName: String,
    val allowedOrigins: Set<String>,
)
