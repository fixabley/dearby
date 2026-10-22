package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.ReceiptsRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.BUSINESS_CARDS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.RECEIPTS
import io.wid.dearby.application.card.CardRepository
import io.wid.dearby.application.card.WalletRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.BusinessCard
import io.wid.dearby.domain.card.ExchangeContext
import io.wid.dearby.domain.card.Receipt
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

// 철회·삭제된 명함(논리키)과 회수된 행은 결과에서 뺀다. context jsonb에는 activityId만 쓴다(옛 label은 읽을 때 무시)
@Repository
class JooqWalletRepository(private val dsl: DSLContext, private val cards: CardRepository) : WalletRepository {

    override fun receipts(recipientId: UserId): List<Receipt> {
        val rows = dsl.selectFrom(RECEIPTS).where(RECEIPTS.RECIPIENT_ID.eq(recipientId), RECEIPTS.RECALLED_AT.isNull).orderBy(RECEIPTS.SEQ).fetch()
        val cardById = cards.findAllById(rows.map { it.cardId!! }).associateBy { it.id }
        val contexts = dsl.exchangeContexts(rows.map { activityId(it) })
        val holders = holdersOfCardsOwnedBy(recipientId)
        return rows.mapNotNull { row -> cardById[row.cardId]?.let { receipt(row, it, contexts.contextOf(activityId(row)), holders) } }
    }

    override fun findReceipt(recipientId: UserId, cardId: UUID): Receipt? {
        val row = dsl.selectFrom(RECEIPTS).where(RECEIPTS.RECIPIENT_ID.eq(recipientId), RECEIPTS.CARD_ID.eq(cardId), RECEIPTS.RECALLED_AT.isNull).fetchOne() ?: return null
        val card = cards.find(cardId) ?: return null
        return receipt(row, card, dsl.exchangeContexts(listOf(activityId(row))).contextOf(activityId(row)), holdersOfCardsOwnedBy(recipientId))
    }

    override fun createReceipt(id: UUID, recipientId: UserId, cardId: UUID, activityId: Id?, receivedAt: OffsetDateTime) {
        val context = JSONB.valueOf(ProfileJson.write(mapOf("activityId" to activityId)))
        dsl.insertInto(RECEIPTS)
            .set(RECEIPTS.ID, id)
            .set(RECEIPTS.RECIPIENT_ID, recipientId)
            .set(RECEIPTS.CARD_ID, cardId)
            .set(RECEIPTS.CONTEXT, context)
            .set(RECEIPTS.RECEIVED_AT, receivedAt)
            .onConflict(RECEIPTS.RECIPIENT_ID, RECEIPTS.CARD_ID).doUpdate()
            .set(RECEIPTS.CONTEXT, context).set(RECEIPTS.RECEIVED_AT, receivedAt).setNull(RECEIPTS.RECALLED_AT)
            .execute()
    }

    override fun setActivity(receiptId: UUID, activityId: Id) {
        dsl.update(RECEIPTS).set(RECEIPTS.CONTEXT, JSONB.valueOf(ProfileJson.write(mapOf("activityId" to activityId)))).where(RECEIPTS.ID.eq(receiptId)).execute()
    }

    override fun activityExists(activityId: Id) = dsl.fetchExists(ACTIVITIES, ACTIVITIES.ID.eq(activityId))

    // 맞교환이 아닌 것만: 명함 주인(mine.user_id)이 받는 사람의 미철회 명함(theirs)을 회수되지 않은 상태로 갖고 있지 않을 때
    override fun recallFromOwner(recipientId: UserId, ownerId: UserId, now: OffsetDateTime) {
        dsl.update(RECEIPTS).set(RECEIPTS.RECALLED_AT, now)
            .where(RECEIPTS.RECIPIENT_ID.eq(recipientId), RECEIPTS.RECALLED_AT.isNull, RECEIPTS.CARD_ID.`in`(dsl.select(BUSINESS_CARDS.ID).from(BUSINESS_CARDS).where(BUSINESS_CARDS.USER_ID.eq(ownerId))))
            .execute()
    }

    override fun recallOneWay(receivedBefore: OffsetDateTime, now: OffsetDateTime): Int {
        val r = RECEIPTS.`as`("r")
        val mine = BUSINESS_CARDS.`as`("mine")
        val back = RECEIPTS.`as`("back")
        val theirs = BUSINESS_CARDS.`as`("theirs")
        val reciprocal = dsl.selectOne().from(mine).join(back).on(back.RECIPIENT_ID.eq(mine.USER_ID)).join(theirs).on(theirs.ID.eq(back.CARD_ID))
            .where(mine.ID.eq(r.CARD_ID), back.RECALLED_AT.isNull, theirs.USER_ID.eq(r.RECIPIENT_ID), theirs.REVOKED.isFalse)
        return dsl.update(r).set(r.RECALLED_AT, now)
            .where(r.RECALLED_AT.isNull, r.RECEIVED_AT.lt(receivedBefore), DSL.notExists(reciprocal))
            .execute()
    }

    // 내(owner) 명함을 회수되지 않은 상태로 가진 사용자들. 받은 명함의 주인이 여기 있으면 맞교환이다
    private fun holdersOfCardsOwnedBy(owner: UserId): Set<UserId> =
        dsl.selectDistinct(RECEIPTS.RECIPIENT_ID).from(RECEIPTS).join(BUSINESS_CARDS).on(BUSINESS_CARDS.ID.eq(RECEIPTS.CARD_ID))
            .where(BUSINESS_CARDS.USER_ID.eq(owner), BUSINESS_CARDS.REVOKED.isFalse, RECEIPTS.RECALLED_AT.isNull)
            .fetch { it.value1()!! }.toSet()

    private fun receipt(row: ReceiptsRecord, card: BusinessCard, context: ExchangeContext, holders: Set<UserId>): Receipt {
        val reciprocal = card.ownerId in holders
        return Receipt(row.id!!, row.recipientId!!, card, context, row.receivedAt!!, reciprocal, if (reciprocal) null else row.receivedAt!!.plus(Receipt.RECALL_AFTER))
    }

    private fun activityId(row: ReceiptsRecord): Id? = ProfileJson.read<ContextJson>(row.context!!.data()).activityId

    // 옛 행에는 label도 있다. 읽기만 하고 다시 쓰지 않는다
    data class ContextJson(val activityId: Long? = null, val label: String? = null)
}
