package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.*
import io.wid.dearby.application.card.CardRepository
import io.wid.dearby.application.guest.GuestHandoff
import io.wid.dearby.application.guest.GuestRepository
import io.wid.dearby.application.guest.GuestWallet
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Repository
class JooqGuestRepository(private val dsl: DSLContext, private val cards: CardRepository) : GuestRepository {

    override fun sessionExists(digest: String) = dsl.fetchExists(GUEST_SESSIONS, GUEST_SESSIONS.DIGEST.eq(digest))

    override fun sessionCount() = dsl.fetchCount(GUEST_SESSIONS)

    override fun createSession(digest: String) {
        dsl.insertInto(GUEST_SESSIONS).set(GUEST_SESSIONS.DIGEST, digest).set(GUEST_SESSIONS.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC)).execute()
    }

    override fun deleteSession(digest: String) = dsl.deleteFrom(GUEST_SESSIONS).where(GUEST_SESSIONS.DIGEST.eq(digest)).execute() > 0

    // 철회됐거나 지워진 명함(논리키)의 참조를 지운다
    override fun pruneRevoked(digest: String) {
        dsl.deleteFrom(GUEST_CARDS).where(
            GUEST_CARDS.SESSION_DIGEST.eq(digest),
            GUEST_CARDS.CARD_ID.notIn(dsl.select(BUSINESS_CARDS.ID).from(BUSINESS_CARDS).where(BUSINESS_CARDS.REVOKED.isFalse)),
        ).execute()
    }

    override fun hasCard(digest: String, cardId: UUID) = dsl.fetchExists(GUEST_CARDS, GUEST_CARDS.SESSION_DIGEST.eq(digest), GUEST_CARDS.CARD_ID.eq(cardId))

    override fun cardCount(digest: String) = dsl.fetchCount(GUEST_CARDS, GUEST_CARDS.SESSION_DIGEST.eq(digest))

    override fun addCard(digest: String, cardId: UUID) {
        dsl.insertInto(GUEST_CARDS).set(GUEST_CARDS.SESSION_DIGEST, digest).set(GUEST_CARDS.CARD_ID, cardId).execute()
    }

    override fun removeCard(digest: String, cardId: UUID) {
        dsl.deleteFrom(GUEST_CARDS).where(GUEST_CARDS.SESSION_DIGEST.eq(digest), GUEST_CARDS.CARD_ID.eq(cardId)).execute()
    }


    override fun wallet(digest: String): GuestWallet {
        val cardIds = dsl.select(GUEST_CARDS.CARD_ID).from(GUEST_CARDS).where(GUEST_CARDS.SESSION_DIGEST.eq(digest)).orderBy(GUEST_CARDS.SEQ).fetch { it.value1()!! }
        val byId = cards.findAllById(cardIds).associateBy { it.id }
        return GuestWallet(cardIds.mapNotNull { byId[it] })
    }

    override fun putHandoff(handoff: GuestHandoff) {
        dsl.deleteFrom(GUEST_HANDOFFS).where(GUEST_HANDOFFS.SESSION_DIGEST.eq(handoff.sessionDigest)).execute()
        dsl.insertInto(GUEST_HANDOFFS)
            .set(GUEST_HANDOFFS.DIGEST, handoff.digest).set(GUEST_HANDOFFS.SESSION_DIGEST, handoff.sessionDigest)
            .set(GUEST_HANDOFFS.TOKEN_CIPHERTEXT, handoff.tokenCiphertext).set(GUEST_HANDOFFS.EXPIRES_AT, handoff.expiresAt)
            .set(GUEST_HANDOFFS.USED_AT, handoff.usedAt).execute()
    }

    override fun findHandoff(digest: String): GuestHandoff? =
        dsl.selectFrom(GUEST_HANDOFFS).where(GUEST_HANDOFFS.DIGEST.eq(digest)).fetchOne()
            ?.let { GuestHandoff(it.digest!!, it.sessionDigest!!, it.tokenCiphertext, it.expiresAt!!, it.usedAt) }

    override fun markHandoffUsed(digest: String, usedAt: OffsetDateTime) {
        dsl.update(GUEST_HANDOFFS).set(GUEST_HANDOFFS.USED_AT, usedAt).setNull(GUEST_HANDOFFS.TOKEN_CIPHERTEXT).where(GUEST_HANDOFFS.DIGEST.eq(digest)).execute()
    }

    override fun clearExpiredHandoffs(now: OffsetDateTime) {
        dsl.update(GUEST_HANDOFFS).setNull(GUEST_HANDOFFS.TOKEN_CIPHERTEXT)
            .where(GUEST_HANDOFFS.EXPIRES_AT.le(now), GUEST_HANDOFFS.TOKEN_CIPHERTEXT.isNotNull).execute()
    }
}
