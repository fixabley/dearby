package io.wid.dearby.application.auth

// 애플이 서명한 identity token에서 확인한 계정. subject(sub)가 계정 식별자다. 이름은 첫 인가 때 클라이언트가 함께 보낸 값
data class AppleIdentity(val subject: String, val email: String?, val displayName: String?)
