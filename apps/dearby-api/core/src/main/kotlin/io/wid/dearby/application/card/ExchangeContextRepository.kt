package io.wid.dearby.application.card

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.ExchangeRef
import java.time.OffsetDateTime

// 교환 세션 맥락 판정(native-v1.md "명함 교환" → "교환 세션 맥락"). 구현은 adaptor/persistence
interface ExchangeContextRepository {

    // now 시각에 그 활동 안에서 받는 사람이 어디 있는지: (1) recipientId가 찜한 세션 중 진행 중인 것 → 세션과 그 블록,
    // (2) 없으면 진행 중인 일정 블록 하나(지운 블록·모집 기간 제외, 겹치면 가장 짧은 것), (3) 없으면 활동만. 활동이 없으면 null
    fun locate(activityId: Id, recipientId: UserId?, now: OffsetDateTime): ExchangeRef?
}
