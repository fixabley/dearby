package io.wid.dearby.adaptor.web.v1.wallet

import io.wid.dearby.domain.card.ExchangeContext

// 어느 활동에서 교환했는지(native-v1.md "명함 교환"). 활동이 지워졌으면 id만 남고 이름은 null
data class ExchangeContextResponse(val activityId: Long?, val activityName: String?, val programName: String?) {
    companion object {
        fun from(context: ExchangeContext) = ExchangeContextResponse(context.activityId, context.activityName, context.programName)
    }
}
