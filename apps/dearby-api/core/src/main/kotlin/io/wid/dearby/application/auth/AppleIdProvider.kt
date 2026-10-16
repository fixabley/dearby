package io.wid.dearby.application.auth

import java.net.URI
import java.util.*

// 애플 쪽 포트. 구현(애플 공개키 검증, 설정값)은 adaptor/security
interface AppleIdProvider {

    // 서명·발급자·대상(앱 ID 또는 Services ID)·만료와 nonce가 맞으면 sub·email을 돌려준다. 아니면 AuthenticationFailedException
    fun verify(identityToken: String, nonce: String): AppleIdentity

    // 대상 ID가 설정되지 않은 환경(애플 로그인을 쓰지 않음)이면 false
    fun isConfigured(): Boolean

    // 웹 방식 인가 주소(Services ID·Return URL로 form_post). 설정이 없으면 ServiceUnavailableException
    fun authorizeUrl(state: UUID, nonce: String): String

    // 콜백이 결과(code 또는 error)를 붙여 앱으로 돌려보낼 주소
    fun appRedirect(name: String, value: String): URI
}
