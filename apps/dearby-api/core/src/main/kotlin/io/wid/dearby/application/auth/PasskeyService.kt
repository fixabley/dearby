package io.wid.dearby.application.auth

import tools.jackson.databind.JsonNode
import java.util.*

// 세션 없이 challengeId로 이어지는 패스키 가입·로그인. 구현은 adaptor/security
interface PasskeyService {

    fun registrationOptions(displayName: String?): PasskeyOptions

    // 검증에 성공하면 계정을 만들고 토큰을 발급한다
    // challenge 없음·만료·재사용, 검증 실패, 모르는 패스키 → AuthenticationFailedException. 형식 오류 → InvalidInputException
    fun register(challengeId: UUID, credential: JsonNode): TokenPair

    fun authenticationOptions(): PasskeyOptions

    fun authenticate(challengeId: UUID, credential: JsonNode): TokenPair

    companion object {
        const val DEFAULT_DISPLAY_NAME = "Dearby" // OS 패스키 목록에 보이는 계정 이름(2026-10-15 사용자 결정)
        const val MAX_DISPLAY_NAME = 64
    }
}
