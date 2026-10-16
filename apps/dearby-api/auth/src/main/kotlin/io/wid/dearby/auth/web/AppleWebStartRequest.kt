package io.wid.dearby.auth.web

// verifierHash = 앱이 만든 무작위 verifier의 SHA-256(base64url, 패딩 없음)
data class AppleWebStartRequest(val verifierHash: String)
