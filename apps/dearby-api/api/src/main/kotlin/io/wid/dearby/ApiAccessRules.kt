package io.wid.dearby

import io.wid.dearby.auth.security.AccessRules
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.AuthorizeHttpRequestsDsl
import org.springframework.stereotype.Component

// 공개 API: 활동 조회는 로그인 없이, 나머지는 로그인 필요
@Component
class ApiAccessRules : AccessRules {

    override fun apply(rules: AuthorizeHttpRequestsDsl) = with(rules) {
        authorize(HttpMethod.GET, "/v1/activities", permitAll)
        authorize(HttpMethod.GET, "/v1/activities/*", permitAll)
        // 공개 명함·공유(native-v1.md "프로필·명함 v2")
        authorize(HttpMethod.GET, "/v1/cards/*", permitAll)
        authorize(HttpMethod.GET, "/v1/shares/*", permitAll)
        authorize(anyRequest, authenticated)
    }
}
