package io.wid.dearby.domain.card

import io.wid.dearby.domain.Id

// 받은 자리: 등록 활동 또는 자유 입력, 둘 다 null일 수 있다
data class ExchangeContext(val activityId: Id?, val label: String?)
