package io.wid.dearby.adaptor.mail

import org.springframework.boot.context.properties.ConfigurationProperties

// 인증번호 메일(native-v1.md "연락처 인증"). host나 from이 비면 /v1/me/contacts/email/*는 503(이 설정이 없어도 기동한다).
// Spring Boot의 spring.mail.* 자동 설정 대신 쓰는 이유: 환경변수가 비어 있을 때 host=""로 메일 발송기가 만들어지지 않게 하려고
@ConfigurationProperties("dearby.mail")
data class MailProperties(
    val host: String? = null,
    val port: Int = 587,
    val username: String? = null,
    val password: String? = null,
    val from: String? = null, // 예: Dearby <no-reply@wid.io.kr>
    val starttls: Boolean = true, // 587 STARTTLS. 로컬 mailpit은 false
)
