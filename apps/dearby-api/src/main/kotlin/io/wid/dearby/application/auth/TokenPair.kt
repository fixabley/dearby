package io.wid.dearby.application.auth

// 응답은 토큰만. 사용자 id·역할·만료는 access token의 sub·roles·exp 클레임에 있다
data class TokenPair(val accessToken: String, val refreshToken: String)
