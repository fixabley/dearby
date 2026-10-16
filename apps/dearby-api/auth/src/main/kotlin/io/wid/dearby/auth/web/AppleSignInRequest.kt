package io.wid.dearby.auth.web

import java.util.*

// displayName은 첫 인가 때 iOS가 받은 이름(이후에는 애플이 주지 않는다)
data class AppleSignInRequest(val challengeId: UUID, val identityToken: String, val displayName: String? = null)
