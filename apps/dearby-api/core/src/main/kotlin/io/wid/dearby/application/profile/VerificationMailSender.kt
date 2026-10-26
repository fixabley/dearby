package io.wid.dearby.application.profile

// 인증번호 메일 포트(native-v1.md "연락처 인증"). 구현(Spring Mail)은 api 모듈 adaptor/mail. 어드민에는 구현이 없어 서비스가 null로 받는다
interface VerificationMailSender {

    // SMTP 호스트와 보내는 주소가 있으면 true
    fun isConfigured(): Boolean

    // 제목 `[Dearby] 이메일 인증번호 123456`, 한글 평문. 보내지 못하면 ServiceUnavailableException(503)
    fun sendCode(to: String, code: String)
}
