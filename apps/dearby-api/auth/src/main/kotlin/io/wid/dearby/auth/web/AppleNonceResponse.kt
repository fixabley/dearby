package io.wid.dearby.auth.web

import java.util.*

data class AppleNonceResponse(val challengeId: UUID, val nonce: String)
