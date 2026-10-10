package io.wid.dearby.adaptor.security

import org.springframework.boot.context.properties.ConfigurationProperties
import java.nio.file.Path
import java.time.Duration

@ConfigurationProperties("dearby.jwt")
data class JwtProperties(
    val keyPath: Path, // RSA 개인키(PKCS#8 PEM). 없으면 기동 시 생성. 여러 서버·재배포 간에 유지되는 경로여야 함
    val issuer: String,
    val accessTokenTtl: Duration,
    val refreshTokenTtl: Duration,
)
