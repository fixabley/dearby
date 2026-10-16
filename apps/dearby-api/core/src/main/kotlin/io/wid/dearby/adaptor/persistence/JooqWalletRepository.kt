package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.BUSINESS_CARDS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.CARD_SHARES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.RECEIPTS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.WALLET_SHARES
import io.wid.dearby.application.card.CardRepository
import io.wid.dearby.application.card.WalletRepository
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.ExchangeContext
import io.wid.dearby.domain.card.Receipt
import io.wid.dearby.domain.card.WalletShare
import org.jooq.DSLContext
import org.jooq.JSONB
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

// 철회·삭제된 명함(논리키)은 결과에서 뺀다
@Repository
class JooqWalletRepository(private val dsl: DSLContext, private val cards: CardRepository) : WalletRepository {

    override fun receipts(recipientId: UserId): List<Receipt> {
        val rows = dsl.selectFrom(RECEIPTS).where(RECEIPTS.RECIPIENT_ID.eq(recipientId)).orderBy(RECEIPTS.RECEIVED_AT, RECEIPTS.SEQ).fetch()
        val cardById = cards.findAllById(rows.map { it.cardId!! }).associateBy { it.id }
        return rows.mapNotNull { row ->
            val card = cardById[row.cardId] ?: return@mapNotNull null
            Receipt(row.id!!, row.recipientId!!, card, ProfileJson.read<ContextJson>(row.context!!.data()).let { ExchangeContext(it.activityId, it.label) }, row.receivedAt!!)
        }
    }

    override fun findReceipt(recipientId: UserId, cardId: UUID): Receipt? {
        val row = dsl.selectFrom(RECEIPTS).where(RECEIPTS.RECIPIENT_ID.eq(recipientId), RECEIPTS.CARD_ID.eq(cardId)).fetchOne() ?: return null
        val card = cards.find(cardId) ?: return null
        return Receipt(row.id!!, row.recipientId!!, card, ProfileJson.read<ContextJson>(row.context!!.data()).let { ExchangeContext(it.activityId, it.label) }, row.receivedAt!!)
    }

    override fun createReceipt(id: UUID, recipientId: UserId, cardId: UUID, context: ExchangeContext, receivedAt: OffsetDateTime) {
        dsl.insertInto(RECEIPTS)
            .set(RECEIPTS.ID, id)
            .set(RECEIPTS.RECIPIENT_ID, recipientId)
            .set(RECEIPTS.CARD_ID, cardId)
            .set(RECEIPTS.CONTEXT, JSONB.valueOf(ProfileJson.write(ContextJson(context.activityId, context.label))))
            .set(RECEIPTS.RECEIVED_AT, receivedAt)
            .execute()
    }

    override fun shares(recipientId: UserId): List<WalletShare> =
        dsl.select(WALLET_SHARES.RECEIPT_ID, WALLET_SHARES.CARD_ID, WALLET_SHARES.SHARE_ID, WALLET_SHARES.SAVED_AT, CARD_SHARES.ACTIVITIES)
            .from(WALLET_SHARES)
            .join(RECEIPTS).on(RECEIPTS.ID.eq(WALLET_SHARES.RECEIPT_ID))
            .join(BUSINESS_CARDS).on(BUSINESS_CARDS.ID.eq(WALLET_SHARES.CARD_ID))
            .join(CARD_SHARES).on(CARD_SHARES.ID.eq(WALLET_SHARES.SHARE_ID))
            .where(RECEIPTS.RECIPIENT_ID.eq(recipientId), BUSINESS_CARDS.REVOKED.isFalse)
            .orderBy(WALLET_SHARES.SEQ)
            .fetch()
            .map { WalletShare(it.value1()!!, it.value2()!!, it.value3()!!, JooqShareRepository.readActivities(it.value5()!!.data()), it.value4()!!) }

    override fun existsShare(receiptId: UUID, shareId: UUID): Boolean =
        dsl.fetchExists(WALLET_SHARES, WALLET_SHARES.RECEIPT_ID.eq(receiptId), WALLET_SHARES.SHARE_ID.eq(shareId))

    override fun countShares(receiptId: UUID): Int = dsl.fetchCount(WALLET_SHARES, WALLET_SHARES.RECEIPT_ID.eq(receiptId))

    override fun addShare(receiptId: UUID, cardId: UUID, shareId: UUID, savedAt: OffsetDateTime) {
        dsl.insertInto(WALLET_SHARES)
            .set(WALLET_SHARES.RECEIPT_ID, receiptId)
            .set(WALLET_SHARES.CARD_ID, cardId)
            .set(WALLET_SHARES.SHARE_ID, shareId)
            .set(WALLET_SHARES.SAVED_AT, savedAt)
            .execute()
    }

    data class ContextJson(val activityId: Long?, val label: String?)
}
