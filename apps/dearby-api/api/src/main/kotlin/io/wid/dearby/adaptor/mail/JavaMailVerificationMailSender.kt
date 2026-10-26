package io.wid.dearby.adaptor.mail

import io.wid.dearby.application.profile.VerificationMailSender
import io.wid.dearby.domain.ServiceUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.stereotype.Component

@Configuration
@EnableConfigurationProperties(MailProperties::class)
class MailConfig {

    // SMTP 호스트가 있을 때만 발송기를 만든다. 테스트는 JavaMailSender를 목으로 바꿔 넣는다
    @Bean
    @ConditionalOnExpression("!'\${dearby.mail.host:}'.isBlank()")
    fun javaMailSender(properties: MailProperties): JavaMailSender = JavaMailSenderImpl().apply {
        host = properties.host
        port = properties.port
        username = properties.username
        password = properties.password
        defaultEncoding = "UTF-8"
        javaMailProperties["mail.smtp.auth"] = (!properties.username.isNullOrBlank()).toString()
        javaMailProperties["mail.smtp.starttls.enable"] = properties.starttls.toString()
        javaMailProperties["mail.smtp.connectiontimeout"] = "5000"
        javaMailProperties["mail.smtp.timeout"] = "10000"
        javaMailProperties["mail.smtp.writetimeout"] = "10000"
    }
}

// 인증번호 메일(native-v1.md "연락처 인증"): 한글 평문 한 통
@Component
class JavaMailVerificationMailSender(private val properties: MailProperties, sender: ObjectProvider<JavaMailSender>) : VerificationMailSender {

    private val log = LoggerFactory.getLogger(javaClass)
    private val sender: JavaMailSender? = sender.ifAvailable

    override fun isConfigured(): Boolean = sender != null && !properties.from.isNullOrBlank()

    override fun sendCode(to: String, code: String) {
        val sender = sender ?: throw ServiceUnavailableException("이메일 인증을 지금 쓸 수 없습니다", "mail sender missing")
        val message = SimpleMailMessage().apply {
            from = properties.from
            setTo(to)
            subject = "[Dearby] 이메일 인증번호 $code"
            text = """
                |Dearby 프로필의 이메일 연락처를 확인하는 인증번호예요.
                |
                |인증번호: $code
                |
                |10분 안에 앱의 인증번호 칸에 입력해 주세요. 요청하지 않았다면 이 메일을 무시해도 돼요. 누구에게도 인증번호를 알려 주지 마세요.
                |
                |팀 WID · Dearby
                """.trimMargin()
        }
        try {
            sender.send(message)
        } catch (e: MailException) {
            log.warn("Verification mail failed: {}", e.message)
            throw ServiceUnavailableException("인증 메일을 보내지 못했어요. 잠시 후 다시 시도해 주세요", "mail send failed: ${e.message}")
        }
    }
}
