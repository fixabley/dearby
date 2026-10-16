package io.wid.dearby.application.auth

// 단계마다 쓰는 값만 채운다(AppleSignInStep 참고)
data class AppleSignInState(
    val nonce: String? = null,
    val verifierHash: String? = null,
    val identity: AppleIdentity? = null,
)
