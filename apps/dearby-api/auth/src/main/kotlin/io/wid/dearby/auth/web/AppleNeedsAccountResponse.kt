package io.wid.dearby.auth.web

import java.util.*

// 202: 처음 보는 애플 계정. 새 계정(POST /v1/auth/apple/register) 또는 패스키 계정 연결(POST /v1/me/identities/apple)
data class AppleNeedsAccountResponse(val linkToken: UUID, val email: String?)
