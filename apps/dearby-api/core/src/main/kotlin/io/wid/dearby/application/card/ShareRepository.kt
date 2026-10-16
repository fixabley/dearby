package io.wid.dearby.application.card

import io.wid.dearby.domain.card.CardShare
import java.util.*

// 공유 기록 저장소. 구현은 adaptor/persistence
interface ShareRepository {

    fun create(share: CardShare)

    fun find(id: UUID): CardShare?

    fun findAllById(ids: Collection<UUID>): List<CardShare>
}
