package io.wid.dearby.application.auth

import java.net.URI
import java.util.*

// 구글 쪽 포트(native-v1.md "구글 로그인"). 구현(JWKS 검증, 설정값)은 adaptor/security
interface GoogleIdProvider {

    // 서명·발급자·대상(client_id)·만료와 nonce가 맞으면 sub·email(인증된 것만)·name을 돌려준다. 아니면 AuthenticationFailedException
    fun verify(idToken: String, nonce: String): AppleIdentity

    // client_id·client_secret·callback URL이 모두 설정되지 않았으면 false
    fun isConfigured(): Boolean

    // 인가 주소(response_type=code, scope openid email profile, prompt=select_account)
    fun authorizeUrl(state: UUID, nonce: String): String

    // 콜백이 결과(code 또는 error)를 붙여 앱으로 돌려보낼 주소(dearby://auth/google)
    fun appRedirect(name: String, value: String): URI
}
