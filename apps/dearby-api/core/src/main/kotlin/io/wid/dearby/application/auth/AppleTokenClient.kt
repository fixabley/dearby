package io.wid.dearby.application.auth

// 애플 토큰 엔드포인트 포트(client secret은 .p8 키로 서명). 구현은 adaptor/security
interface AppleTokenClient {

    // authorization code를 바꿔 refresh token을 받는다. 키가 설정되지 않았거나 실패하면 null(로그인은 막지 않는다)
    fun refreshToken(authorizationCode: String, clientId: String): String?

    // 계정 삭제 때 토큰을 폐기한다. 실패하면 false(삭제는 막지 않는다)
    fun revoke(refreshToken: String, clientId: String): Boolean
}
