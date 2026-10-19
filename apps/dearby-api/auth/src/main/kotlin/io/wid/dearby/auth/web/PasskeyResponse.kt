package io.wid.dearby.auth.web

import java.time.OffsetDateTime

data class PasskeyListResponse(val items: List<PasskeyResponse>)

// id는 credentialId(base64url). backedUp은 기기 동기화(backup state), transports는 등록 때 기기가 알려 준 값(iOS는 빈 배열)
data class PasskeyResponse(
    val id: String,
    val label: String,
    val createdAt: OffsetDateTime,
    val lastUsedAt: OffsetDateTime,
    val backedUp: Boolean,
    val transports: List<String>,
)

data class PasskeyLabelRequest(val label: String)
