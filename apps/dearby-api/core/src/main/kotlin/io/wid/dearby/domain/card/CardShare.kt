package io.wid.dearby.domain.card

import java.time.OffsetDateTime
import java.util.*

// 공유 기록. id가 공개 링크 /s/<id>다. activities는 공유 시점의 복사본이며 참가 증명이 아니다
data class CardShare(val id: UUID, val cardId: UUID, val activities: List<SharedActivity>, val createdAt: OffsetDateTime)
