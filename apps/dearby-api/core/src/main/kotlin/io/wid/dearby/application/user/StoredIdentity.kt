package io.wid.dearby.application.user

import java.time.OffsetDateTime

// 사용자에 연결된 외부 로그인 계정. 설정 화면 목록(email·displayName·createdAt)과 애플 토큰 폐기(clientId·refreshToken)에 쓴다
data class StoredIdentity(
    val provider: String,
    val subject: String,
    val clientId: String?,
    val refreshToken: String?,
    val email: String?,
    val displayName: String?,
    val createdAt: OffsetDateTime,
)
