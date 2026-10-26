package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.BusinessCardsRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.BUSINESS_CARDS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.BUSINESS_CARD_PHOTOS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROFILE_PHOTOS
import io.wid.dearby.application.card.CardRepository
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.BusinessCard
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import java.time.OffsetDateTime
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class JooqCardRepository(private val dsl: DSLContext) : CardRepository {

    override fun create(card: BusinessCard) {
        dsl.insertInto(BUSINESS_CARDS)
            .set(BUSINESS_CARDS.ID, card.id)
            .set(BUSINESS_CARDS.USER_ID, card.ownerId)
            .set(BUSINESS_CARDS.NAME, card.name)
            .set(BUSINESS_CARDS.DESCRIPTION, card.description)
            .set(BUSINESS_CARDS.TEMPLATE, card.template)
            .set(BUSINESS_CARDS.INTRODUCTION, "") // 소개 글 폐지(2026-10-26): 새 명함·고친 명함은 본문이 비어 있다
            .set(BUSINESS_CARDS.INTRO_LINK, card.introLink)
            .set(BUSINESS_CARDS.PROFILE_NAME, card.profileName)
            .set(BUSINESS_CARDS.JOB, card.job)
            .set(BUSINESS_CARDS.CONTACTS, JSONB.valueOf(ProfileJson.contacts(card.contacts)))
            .set(BUSINESS_CARDS.HISTORIES, JSONB.valueOf(ProfileJson.histories(card.histories)))
            .set(BUSINESS_CARDS.REVOKED, false)
            .set(BUSINESS_CARDS.CREATED_AT, card.createdAt)
            .set(BUSINESS_CARDS.UPDATED_AT, card.updatedAt)
            .execute()
    }

    override fun update(card: BusinessCard): Boolean =
        dsl.update(BUSINESS_CARDS)
            .set(BUSINESS_CARDS.NAME, card.name)
            .set(BUSINESS_CARDS.DESCRIPTION, card.description)
            .set(BUSINESS_CARDS.TEMPLATE, card.template)
            .set(BUSINESS_CARDS.INTRODUCTION, "") // 소개 글 폐지(2026-10-26): 새 명함·고친 명함은 본문이 비어 있다
            .set(BUSINESS_CARDS.INTRO_LINK, card.introLink)
            .set(BUSINESS_CARDS.PROFILE_NAME, card.profileName)
            .set(BUSINESS_CARDS.JOB, card.job)
            .set(BUSINESS_CARDS.CONTACTS, JSONB.valueOf(ProfileJson.contacts(card.contacts)))
            .set(BUSINESS_CARDS.HISTORIES, JSONB.valueOf(ProfileJson.histories(card.histories)))
            .set(BUSINESS_CARDS.UPDATED_AT, card.updatedAt)
            .where(BUSINESS_CARDS.ID.eq(card.id), BUSINESS_CARDS.REVOKED.isFalse)
            .execute() > 0

    override fun find(id: UUID): BusinessCard? =
        dsl.selectFrom(BUSINESS_CARDS).where(BUSINESS_CARDS.ID.eq(id), BUSINESS_CARDS.REVOKED.isFalse).fetchOne()?.let(::toCard)

    override fun findAllById(ids: Collection<UUID>): List<BusinessCard> {
        if (ids.isEmpty()) return emptyList()
        return dsl.selectFrom(BUSINESS_CARDS).where(BUSINESS_CARDS.ID.`in`(ids), BUSINESS_CARDS.REVOKED.isFalse).fetch().map(::toCard)
    }

    override fun listByOwner(ownerId: UserId): List<BusinessCard> =
        dsl.selectFrom(BUSINESS_CARDS).where(BUSINESS_CARDS.USER_ID.eq(ownerId), BUSINESS_CARDS.REVOKED.isFalse).orderBy(BUSINESS_CARDS.SEQ).fetch().map(::toCard)

    // 지우기(2026-10-25 사용자 결정, native-v1.md "명함 지우기"): 행과 id·주인·시각은 남기고(QR·받은 명함·신고가 id를 가리킨다) 내용은 바로 비운다.
    // 비우는 값은 V56과 같다. 철회된 명함은 어디서도 읽지 않으므로 빈 값이 화면에 나가지 않는다
    override fun revoke(id: UUID): Boolean {
        dsl.deleteFrom(BUSINESS_CARD_PHOTOS).where(BUSINESS_CARD_PHOTOS.CARD_ID.eq(id)).execute()
        return dsl.update(BUSINESS_CARDS)
            .set(BUSINESS_CARDS.REVOKED, true)
            .setNull(BUSINESS_CARDS.PHOTO_UPDATED_AT)
            .set(BUSINESS_CARDS.NAME, "")
            .set(BUSINESS_CARDS.DESCRIPTION, "")
            .set(BUSINESS_CARDS.TEMPLATE, "")
            .set(BUSINESS_CARDS.INTRODUCTION, "")
            .setNull(BUSINESS_CARDS.INTRO_LINK)
            .set(BUSINESS_CARDS.PROFILE_NAME, "")
            .set(BUSINESS_CARDS.JOB, "")
            .set(BUSINESS_CARDS.CONTACTS, JSONB.valueOf("[]"))
            .set(BUSINESS_CARDS.HISTORIES, JSONB.valueOf("[]"))
            .where(BUSINESS_CARDS.ID.eq(id), BUSINESS_CARDS.REVOKED.isFalse)
            .execute() > 0
    }

    override fun snapshotPhoto(cardId: UUID, ownerId: UserId, include: Boolean): OffsetDateTime? {
        val copied = if (include) {
            dsl.insertInto(BUSINESS_CARD_PHOTOS, BUSINESS_CARD_PHOTOS.CARD_ID, BUSINESS_CARD_PHOTOS.DATA)
                .select(dsl.select(DSL.`val`(cardId), PROFILE_PHOTOS.DATA).from(PROFILE_PHOTOS).where(PROFILE_PHOTOS.USER_ID.eq(ownerId)))
                .onConflict(BUSINESS_CARD_PHOTOS.CARD_ID).doUpdate().set(BUSINESS_CARD_PHOTOS.DATA, DSL.excluded(BUSINESS_CARD_PHOTOS.DATA))
                .execute() > 0
        } else false
        if (!copied) dsl.deleteFrom(BUSINESS_CARD_PHOTOS).where(BUSINESS_CARD_PHOTOS.CARD_ID.eq(cardId)).execute()
        val version = if (copied) dsl.select(PROFILE_PHOTOS.UPDATED_AT).from(PROFILE_PHOTOS).where(PROFILE_PHOTOS.USER_ID.eq(ownerId)).fetchOne(PROFILE_PHOTOS.UPDATED_AT) else null
        dsl.update(BUSINESS_CARDS).set(BUSINESS_CARDS.PHOTO_UPDATED_AT, version).where(BUSINESS_CARDS.ID.eq(cardId)).execute()
        return version
    }

    override fun photo(cardId: UUID): ByteArray? =
        dsl.select(BUSINESS_CARD_PHOTOS.DATA).from(BUSINESS_CARD_PHOTOS).join(BUSINESS_CARDS).on(BUSINESS_CARDS.ID.eq(BUSINESS_CARD_PHOTOS.CARD_ID))
            .where(BUSINESS_CARD_PHOTOS.CARD_ID.eq(cardId), BUSINESS_CARDS.REVOKED.isFalse).fetchOne(BUSINESS_CARD_PHOTOS.DATA)

    private fun toCard(it: BusinessCardsRecord) = BusinessCard(
        id = it.id!!,
        ownerId = it.userId!!,
        name = it.name!!,
        description = it.description!!,
        template = it.template!!,
        introLink = it.introLink,
        profileName = it.profileName!!,
        job = it.job!!,
        contacts = ProfileJson.readContacts(it.contacts!!.data()),
        histories = ProfileJson.readHistories(it.histories!!.data()),
        createdAt = it.createdAt!!,
        updatedAt = it.updatedAt!!,
        photoUpdatedAt = it.photoUpdatedAt,
    )
}
