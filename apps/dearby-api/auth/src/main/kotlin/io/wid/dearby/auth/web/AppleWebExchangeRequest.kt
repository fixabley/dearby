package io.wid.dearby.auth.web

import java.util.*

data class AppleWebExchangeRequest(val code: UUID, val verifier: String)
