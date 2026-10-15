package io.wid.dearby.auth.security

import org.springframework.security.config.annotation.web.AuthorizeHttpRequestsDsl

// 실행 앱(api·admin)이 자기 경로의 권한 규칙을 정한다. 인증 경로(/v1/auth/**)와 /error는 SecurityConfig가 먼저 연다
fun interface AccessRules {

    fun apply(rules: AuthorizeHttpRequestsDsl)
}
