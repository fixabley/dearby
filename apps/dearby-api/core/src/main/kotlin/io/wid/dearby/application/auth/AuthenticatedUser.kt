package io.wid.dearby.application.auth

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.user.UserRole

// 토큰을 발급받은 사용자. 역할은 발급 시점 기준이라 바뀌면 다음 refresh에 반영된다
data class AuthenticatedUser(val id: UserId, val roles: List<UserRole>)
