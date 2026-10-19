package io.wid.dearby.auth.web

import java.time.OffsetDateTime

data class IdentityListResponse(val items: List<IdentityResponse>)

// provider는 소문자(apple·google). email은 애플이 준 가린 이메일 그대로일 수 있다
data class IdentityResponse(val provider: String, val email: String?, val displayName: String?, val linkedAt: OffsetDateTime)
