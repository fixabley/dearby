package io.wid.dearby.adaptor.web.v1.guest

import java.time.OffsetDateTime

data class HandoffResponse(val code: String, val expiresAt: OffsetDateTime)
