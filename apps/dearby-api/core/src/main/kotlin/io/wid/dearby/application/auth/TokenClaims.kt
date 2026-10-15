package io.wid.dearby.application.auth

// access token 클레임 이름. 발급(api)과 검증(api·admin)이 같이 쓴다
object TokenClaims {
    const val ROLES = "roles" // 사용자 역할 이름 목록(UserRole). 검증 쪽은 "ROLE_" 접두사를 붙여 권한으로 쓴다
}
