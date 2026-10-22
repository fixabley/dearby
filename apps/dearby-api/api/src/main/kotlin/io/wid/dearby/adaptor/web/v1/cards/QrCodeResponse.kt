package io.wid.dearby.adaptor.web.v1.cards

import java.time.OffsetDateTime
import java.util.*

// POST /v1/cards/:id/qr-codes 201. code 원문은 이 응답에만 실린다
data class QrCodeResponse(val id: UUID, val code: String, val expiresAt: OffsetDateTime)
