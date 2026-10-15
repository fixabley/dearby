package io.wid.dearby.admin

import io.wid.dearby.auth.security.AccessRules
import io.wid.dearby.domain.user.UserRole
import org.springframework.security.config.annotation.web.AuthorizeHttpRequestsDsl
import org.springframework.stereotype.Component

// 관리자 API는 ADMIN 역할만. 토큰 없음·무효는 401, ADMIN이 아니면 403
@Component
class AdminAccessRules : AccessRules {

    override fun apply(rules: AuthorizeHttpRequestsDsl) = with(rules) {
        authorize("/v1/admin/**", hasRole(UserRole.ADMIN.name))
        authorize(anyRequest, denyAll)
    }
}
