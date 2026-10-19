package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.BusinessCardsRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.BUSINESS_CARDS
import io.wid.dearby.application.card.CardRepository
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.BusinessCard
import org.jooq.DSLContext
import org.jooq.JSONB
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
            .set(BUSINESS_CARDS.INTRODUCTION, card.introduction)
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
            .set(BUSINESS_CARDS.INTRODUCTION, card.introduction)
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

    override fun revoke(id: UUID): Boolean =
        dsl.update(BUSINESS_CARDS).set(BUSINESS_CARDS.REVOKED, true).where(BUSINESS_CARDS.ID.eq(id), BUSINESS_CARDS.REVOKED.isFalse).execute() > 0

    private fun toCard(it: BusinessCardsRecord) = BusinessCard(
        id = it.id!!,
        ownerId = it.userId!!,
        name = it.name!!,
        description = it.description!!,
        template = it.template!!,
        introduction = it.introduction!!,
        profileName = it.profileName!!,
        job = it.job!!,
        contacts = ProfileJson.readContacts(it.contacts!!.data()),
        histories = ProfileJson.readHistories(it.histories!!.data()),
        createdAt = it.createdAt!!,
        updatedAt = it.updatedAt!!,
    )
}
