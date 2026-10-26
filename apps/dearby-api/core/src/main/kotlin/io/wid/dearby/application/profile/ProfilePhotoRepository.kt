package io.wid.dearby.application.profile

import io.wid.dearby.domain.UserId
import java.time.OffsetDateTime

// 프로필 사진 저장소(사용자당 하나). 구현은 adaptor/persistence
interface ProfilePhotoRepository {

    fun save(userId: UserId, data: ByteArray, updatedAt: OffsetDateTime)

    fun find(userId: UserId): ByteArray?

    fun updatedAt(userId: UserId): OffsetDateTime?

    fun delete(userId: UserId)
}
