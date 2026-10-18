package io.wid.dearby

import io.wid.dearby.auth.security.AccessRules
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.AuthorizeHttpRequestsDsl
import org.springframework.stereotype.Component

// 공개 API: 활동·프로그램 묶음 조회는 로그인 없이, 나머지는 로그인 필요
@Component
class ApiAccessRules : AccessRules {

    override fun apply(rules: AuthorizeHttpRequestsDsl) = with(rules) {
        authorize(HttpMethod.GET, "/v1/activities", permitAll)
        authorize(HttpMethod.GET, "/v1/activities/*", permitAll)
        // 발견 프로그램 묶음(native-v1.md "발견 프로그램 묶음")
        authorize(HttpMethod.GET, "/v1/discovery/programs", permitAll)
        // 공개 명함(native-v1.md "프로필·명함 v2")
        authorize(HttpMethod.GET, "/v1/cards/*", permitAll)
        // 웹 비로그인 저장: 로그인 대신 프록시 키·세션 토큰 헤더를 본다(adaptor/web/v1/guest)
        authorize("/v1/guest/**", permitAll)
        authorize(anyRequest, authenticated)
    }
}
