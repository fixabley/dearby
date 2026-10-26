package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROFILE_PHOTOS
import io.wid.dearby.application.profile.ProfilePhotoRepository
import io.wid.dearby.domain.UserId
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class JooqProfilePhotoRepository(private val dsl: DSLContext) : ProfilePhotoRepository {

    override fun save(userId: UserId, data: ByteArray, updatedAt: OffsetDateTime) {
        dsl.insertInto(PROFILE_PHOTOS)
            .set(PROFILE_PHOTOS.USER_ID, userId)
            .set(PROFILE_PHOTOS.DATA, data)
            .set(PROFILE_PHOTOS.UPDATED_AT, updatedAt)
            .onConflict(PROFILE_PHOTOS.USER_ID).doUpdate()
            .set(PROFILE_PHOTOS.DATA, data)
            .set(PROFILE_PHOTOS.UPDATED_AT, updatedAt)
            .execute()
    }

    override fun find(userId: UserId): ByteArray? =
        dsl.select(PROFILE_PHOTOS.DATA).from(PROFILE_PHOTOS).where(PROFILE_PHOTOS.USER_ID.eq(userId)).fetchOne(PROFILE_PHOTOS.DATA)

    override fun updatedAt(userId: UserId): OffsetDateTime? =
        dsl.select(PROFILE_PHOTOS.UPDATED_AT).from(PROFILE_PHOTOS).where(PROFILE_PHOTOS.USER_ID.eq(userId)).fetchOne(PROFILE_PHOTOS.UPDATED_AT)

    override fun delete(userId: UserId) {
        dsl.deleteFrom(PROFILE_PHOTOS).where(PROFILE_PHOTOS.USER_ID.eq(userId)).execute()
    }
}
