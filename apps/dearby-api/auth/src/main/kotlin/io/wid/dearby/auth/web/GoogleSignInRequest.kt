package io.wid.dearby.auth.web

// idToken은 앱의 구글 SDK가 받은 ID 토큰, nonce는 POST /v1/auth/google/nonce가 준 값(그 토큰의 nonce 클레임과 같아야 한다)
data class GoogleSignInRequest(val idToken: String, val nonce: String)
