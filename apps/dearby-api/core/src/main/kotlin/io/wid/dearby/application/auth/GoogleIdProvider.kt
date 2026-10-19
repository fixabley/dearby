package io.wid.dearby.application.auth

// 구글 쪽 포트(native-v1.md "구글 로그인 — 네이티브"). 구현(JWKS 검증, 허용 client id)은 adaptor/security
interface GoogleIdProvider {

    // 서명·발급자·대상(허용 client id 중 하나)·만료와 nonce가 맞으면 sub·email(인증된 것만)·name을 돌려준다. 아니면 AuthenticationFailedException
    fun verify(idToken: String, nonce: String): AppleIdentity

    // 허용 client id가 하나도 없으면 false
    fun isConfigured(): Boolean
}
