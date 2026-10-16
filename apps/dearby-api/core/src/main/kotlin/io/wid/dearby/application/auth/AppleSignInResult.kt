package io.wid.dearby.application.auth

import java.util.*

sealed interface AppleSignInResult {

    // 이미 연결된 애플 계정
    data class SignedIn(val tokens: TokenPair) : AppleSignInResult

    // 처음 보는 애플 계정. 사용자가 새 계정을 만들지, 기존 패스키 계정에 연결할지 고른다(2026-10-16 사용자 결정)
    data class NeedsAccount(val linkToken: UUID, val email: String?) : AppleSignInResult
}
