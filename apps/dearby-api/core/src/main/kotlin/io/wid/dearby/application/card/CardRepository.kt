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

    // 지우기: 내용과 사진을 비운다
    fun revoke(id: UUID): Boolean

    // 명함 사진 스냅샷(native-v1.md "프로필 사진"): include면 지금 프로필 사진을 복사하고 아니거나 프로필 사진이 없으면 비운다. 복사한 사진의 버전을 돌려준다
    fun snapshotPhoto(cardId: UUID, ownerId: UserId, include: Boolean): java.time.OffsetDateTime?

    // 철회 안 한 명함의 사진
    fun photo(cardId: UUID): ByteArray?
}
