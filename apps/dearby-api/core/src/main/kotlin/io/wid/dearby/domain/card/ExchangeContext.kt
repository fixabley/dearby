package io.wid.dearby.domain.card

import io.wid.dearby.domain.Id

// 어디서 교환했는지의 id(native-v1.md "명함 교환" → "교환 세션 맥락"). 활동(프로그램 기수) → 그 안의 일정 블록 → 블록의 세션 순으로 좁아진다.
// 저장할 때 한 번 판정해 그대로 둔다. 모르는 활동이면 NONE
data class ExchangeRef(val activityId: Id?, val scheduleId: Id?, val sessionId: Id?) {
    companion object {
        val NONE = ExchangeRef(null, null, null)
    }
}

// 저장한 id에 이름을 붙인 것. 지워진 활동·블록·세션은 id만 남고 이름은 null. trackName·room은 세션의 트랙(전폭 세션이면 null)
data class ExchangeContext(
    val ref: ExchangeRef, val activityName: String?, val programName: String?,
    val scheduleTitle: String?, val sessionTitle: String?, val trackName: String?, val room: String?,
) {
    companion object {
        val NONE = ExchangeContext(ExchangeRef.NONE, null, null, null, null, null, null)
    }
}
