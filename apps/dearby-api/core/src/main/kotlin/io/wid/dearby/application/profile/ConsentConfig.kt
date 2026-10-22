package io.wid.dearby.application.profile

import io.wid.dearby.domain.profile.ConsentVersions
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// 동의 문서 버전(native-v1.md "개인정보 동의"): 웹 /privacy·/terms의 시행일. 문서를 바꾸면 값을 올리고, 버전이 다른 사용자는 다시 동의한다.
// core에 두는 이유: ProfileService를 쓰는 api·admin 두 앱이 같은 빈을 가져야 한다
@Configuration
class ConsentConfig {
    @Bean
    fun consentVersions(
        @Value("\${dearby.consent.privacy-version:2026-10-22}") privacy: String,
        @Value("\${dearby.consent.terms-version:2026-10-22}") terms: String,
    ) = ConsentVersions(privacy, terms)
}
