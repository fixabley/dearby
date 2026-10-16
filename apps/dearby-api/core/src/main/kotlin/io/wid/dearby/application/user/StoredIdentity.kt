package io.wid.dearby.application.user

// 사용자에 연결된 외부 로그인 계정 중 토큰 폐기에 필요한 값
data class StoredIdentity(val provider: String, val subject: String, val clientId: String?, val refreshToken: String?)
