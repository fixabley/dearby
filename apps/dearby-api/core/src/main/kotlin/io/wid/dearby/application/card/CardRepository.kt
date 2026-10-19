package io.wid.dearby.application.card

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.BusinessCard
import java.util.*

// 명함 저장소. 구현은 adaptor/persistence
interface CardRepository {

    fun create(card: BusinessCard)

    // 같은 id의 스냅샷 열을 모두 덮어쓴다(seq·created_at 제외). 없거나 철회됐으면 false
    fun update(card: BusinessCard): Boolean

    // 철회한 명함은 없는 것으로 본다
    fun find(id: UUID): BusinessCard?

    fun findAllById(ids: Collection<UUID>): List<BusinessCard>

    // 철회 안 한 내 명함, 만든 순
    fun listByOwner(ownerId: UserId): List<BusinessCard>

    fun revoke(id: UUID): Boolean
}
