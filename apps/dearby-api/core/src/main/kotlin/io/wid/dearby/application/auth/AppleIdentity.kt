package io.wid.dearby.application.auth

// 애플이 서명한 identity token에서 확인한 계정. subject(sub)가 계정 식별자다. 이름은 첫 인가 때 클라이언트가 함께 보낸 값.
// clientId는 토큰의 대상(번들 ID 또는 Services ID), refreshToken은 authorization code를 바꿔 받은 애플 토큰(계정 삭제 때 폐기용, 없을 수 있다)
data class AppleIdentity(
    val subject: String,
    val email: String?,
    val displayName: String?,
    val clientId: String? = null,
    val refreshToken: String? = null,
)
