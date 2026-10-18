package io.wid.dearby.auth.web

import java.util.*

// 202: 처음 보는 구글 계정. 새 계정(POST /v1/auth/google/register) 또는 패스키 계정 연결(POST /v1/me/identities/google)
data class GoogleNeedsAccountResponse(val linkToken: UUID, val email: String?, val displayName: String?)
