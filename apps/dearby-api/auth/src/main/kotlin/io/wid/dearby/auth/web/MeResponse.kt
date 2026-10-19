package io.wid.dearby.auth.web

import java.time.OffsetDateTime
import java.util.*

// GET /v1/me(native-v1.md "설정")
data class MeResponse(val id: UUID, val createdAt: OffsetDateTime, val signInMethods: SignInMethodsResponse)

data class SignInMethodsResponse(val passkeys: Int, val apple: Boolean, val google: Boolean)
