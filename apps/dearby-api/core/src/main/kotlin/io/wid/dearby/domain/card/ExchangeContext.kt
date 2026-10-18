package io.wid.dearby.domain.card

import io.wid.dearby.domain.Id

// 어느 활동(프로그램 기수)에서 교환했는지(native-v1.md "명함 교환"). 활동이 없거나 지워졌으면 이름은 null
data class ExchangeContext(val activityId: Id?, val activityName: String?, val programName: String?) {
    companion object {
        val NONE = ExchangeContext(null, null, null)
    }
}
