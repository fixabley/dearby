package io.wid.dearby.adaptor.web.v1.wallet

import io.wid.dearby.domain.card.ExchangeContext

// 어디서 교환했는지(native-v1.md "명함 교환" → "교환 세션 맥락"): 활동 → 일정 블록 → 세션(과 그 트랙·방) 순으로 좁아진다.
// 지워진 활동·블록·세션은 id만 남고 이름은 null. 세션이 전폭(트랙 없음)이면 trackName·room도 null
data class ExchangeContextResponse(
    val activityId: Long?, val activityName: String?, val programName: String?,
    val scheduleId: Long?, val scheduleTitle: String?,
    val sessionId: Long?, val sessionTitle: String?, val trackName: String?, val room: String?,
) {
    companion object {
        fun from(context: ExchangeContext) = ExchangeContextResponse(
            context.ref.activityId, context.activityName, context.programName,
            context.ref.scheduleId, context.scheduleTitle,
            context.ref.sessionId, context.sessionTitle, context.trackName, context.room,
        )
    }
}
