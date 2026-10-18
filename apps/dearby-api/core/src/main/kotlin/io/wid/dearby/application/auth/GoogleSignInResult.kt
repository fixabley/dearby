package io.wid.dearby.application.auth

import java.util.*

sealed interface GoogleSignInResult {

    // 이미 연결된 구글 계정
    data class SignedIn(val tokens: TokenPair) : GoogleSignInResult

    // 처음 보는 구글 계정. 앱이 기기 패스키를 조용히 확인한 뒤 새 계정 또는 연결을 고른다(애플과 같다)
    data class NeedsAccount(val linkToken: UUID, val email: String?, val displayName: String?) : GoogleSignInResult
}
