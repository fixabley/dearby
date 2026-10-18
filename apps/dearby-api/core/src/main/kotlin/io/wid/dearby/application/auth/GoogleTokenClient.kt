package io.wid.dearby.application.auth

// 구글 토큰 엔드포인트 포트. 구현은 adaptor/security
fun interface GoogleTokenClient {

    // 인가 code를 바꿔 ID 토큰(JWT 문자열)을 받는다. 실패하면 AuthenticationFailedException. refresh token은 받지도 저장하지도 않는다
    fun exchange(code: String): String
}
