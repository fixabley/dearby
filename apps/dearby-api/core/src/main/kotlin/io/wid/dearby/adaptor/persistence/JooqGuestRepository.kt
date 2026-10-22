package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.*
import io.wid.dearby.application.card.CardRepository
import io.wid.dearby.application.guest.GuestHandoff
import io.wid.dearby.application.guest.GuestItem
import io.wid.dearby.application.guest.GuestRepository
import io.wid.dearby.application.guest.GuestWallet
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.card.Receipt
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

// 회수된 저장(recalled_at)은 목록·개수·중복 판정에서 뺀다. 행은 보존한다(native-v1.md "명함 교환")
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

    override fun hasCard(digest: String, cardId: UUID) =
        dsl.fetchExists(GUEST_CARDS, GUEST_CARDS.SESSION_DIGEST.eq(digest), GUEST_CARDS.CARD_ID.eq(cardId), GUEST_CARDS.RECALLED_AT.isNull)

    override fun cardCount(digest: String) = dsl.fetchCount(GUEST_CARDS, GUEST_CARDS.SESSION_DIGEST.eq(digest), GUEST_CARDS.RECALLED_AT.isNull)

    override fun addCard(digest: String, cardId: UUID, activityId: Id?) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl.insertInto(GUEST_CARDS)
            .set(GUEST_CARDS.SESSION_DIGEST, digest).set(GUEST_CARDS.CARD_ID, cardId).set(GUEST_CARDS.ACTIVITY_ID, activityId).set(GUEST_CARDS.SAVED_AT, now)
            .onConflict(GUEST_CARDS.SESSION_DIGEST, GUEST_CARDS.CARD_ID).doUpdate()
            .set(GUEST_CARDS.ACTIVITY_ID, activityId).set(GUEST_CARDS.SAVED_AT, now).setNull(GUEST_CARDS.RECALLED_AT)
            .execute()
    }

    override fun activityExists(activityId: Id) = dsl.fetchExists(ACTIVITIES, ACTIVITIES.ID.eq(activityId))

    override fun removeCard(digest: String, cardId: UUID) {
        dsl.deleteFrom(GUEST_CARDS).where(GUEST_CARDS.SESSION_DIGEST.eq(digest), GUEST_CARDS.CARD_ID.eq(cardId)).execute()
    }

    override fun wallet(digest: String): GuestWallet {
        val rows = dsl.select(GUEST_CARDS.CARD_ID, GUEST_CARDS.ACTIVITY_ID, GUEST_CARDS.SAVED_AT).from(GUEST_CARDS)
            .where(GUEST_CARDS.SESSION_DIGEST.eq(digest), GUEST_CARDS.RECALLED_AT.isNull).orderBy(GUEST_CARDS.SEQ).fetch()
        val byId = cards.findAllById(rows.map { it.value1()!! }).associateBy { it.id }
        val contexts = dsl.exchangeContexts(rows.map { it.value2() })
        return GuestWallet(rows.mapNotNull { row ->
            val savedAt = row.value3()!!
            byId[row.value1()]?.let { GuestItem(it, contexts.contextOf(row.value2()), savedAt, savedAt.plus(Receipt.RECALL_AFTER)) }
        })
    }

    override fun recallFromOwner(digest: String, ownerId: UUID, now: OffsetDateTime) {
        dsl.update(GUEST_CARDS).set(GUEST_CARDS.RECALLED_AT, now)
            .where(GUEST_CARDS.SESSION_DIGEST.eq(digest), GUEST_CARDS.RECALLED_AT.isNull, GUEST_CARDS.CARD_ID.`in`(dsl.select(BUSINESS_CARDS.ID).from(BUSINESS_CARDS).where(BUSINESS_CARDS.USER_ID.eq(ownerId))))
            .execute()
    }

    override fun recallExpired(savedBefore: OffsetDateTime, now: OffsetDateTime): Int =
        dsl.update(GUEST_CARDS).set(GUEST_CARDS.RECALLED_AT, now).where(GUEST_CARDS.RECALLED_AT.isNull, GUEST_CARDS.SAVED_AT.lt(savedBefore)).execute()

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
