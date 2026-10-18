package io.wid.dearby.application.auth

// 단계마다 쓰는 값만 채운다(AppleSignInStep 참고). 구글 로그인도 같은 표를 쓰므로 provider로 구분한다(2026-10-18)
data class AppleSignInState(
    val nonce: String? = null,
    val verifierHash: String? = null,
    val identity: AppleIdentity? = null,
    val provider: String = AppleSignInService.PROVIDER,
)
