package io.wid.dearby.application.auth

import io.wid.dearby.domain.UserId
import tools.jackson.databind.JsonNode
import java.util.*

// 세션 없이 challengeId로 이어지는 패스키 가입·로그인. 구현은 adaptor/security
interface PasskeyService {

    // userId가 있으면(로그인한 사용자, native-v1.md "설정") 그 계정에 패스키를 더하는 options, 없으면 새 계정 가입 options
    fun registrationOptions(displayName: String?, userId: UserId? = null): PasskeyOptions

    // 검증에 성공하면 계정을 만들고(기존 계정에 더하는 경우는 만들지 않고) 토큰을 발급한다
    // challenge 없음·만료·재사용, 검증 실패, 모르는 패스키 → AuthenticationFailedException. 형식 오류 → InvalidInputException
    fun register(challengeId: UUID, credential: JsonNode): TokenPair

    fun authenticationOptions(): PasskeyOptions

    fun authenticate(challengeId: UUID, credential: JsonNode): TokenPair

    companion object {
        const val DEFAULT_DISPLAY_NAME = "Dearby" // OS 패스키 목록에 보이는 계정 이름(2026-10-15 사용자 결정)
        const val MAX_DISPLAY_NAME = 64
    }
}
