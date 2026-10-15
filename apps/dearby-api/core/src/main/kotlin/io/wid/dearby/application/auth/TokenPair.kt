package io.wid.dearby.application.auth

// 로그인·가입·refresh 응답. user는 클라이언트가 화면 권한 등에 쓰는 사용자 id·역할(2026-10-15 사용자 결정).
// 같은 값이 access token의 sub·roles 클레임에도 있고, 서버는 토큰만 믿는다
data class TokenPair(val accessToken: String, val refreshToken: String, val user: AuthenticatedUser)
