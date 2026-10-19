package io.wid.dearby.application.auth

import java.time.Duration
import java.util.*

// 한 번만 꺼낼 수 있는 외부 로그인(애플·구글) 중간 상태. 구현은 adaptor/persistence
interface AppleSignInStateRepository {

    // id를 주면 그 id로 저장한다(구글 nonce처럼 호출자가 id를 정해야 할 때). 없으면 무작위 UUID
    fun issue(step: AppleSignInStep, state: AppleSignInState, id: UUID = UUID.randomUUID()): UUID

    // 유효하면 지우고 돌려준다. 같은 id로 동시에 요청해도 한 번만 성공. provider가 다른 상태는 돌려주지 않는다
    fun consume(id: UUID, step: AppleSignInStep, provider: String = AppleSignInService.PROVIDER): AppleSignInState?

    companion object {
        val TTL: Duration = Duration.ofMinutes(10)
    }
}
