package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.RECEIPTS
import io.wid.dearby.application.card.CardRepository
import io.wid.dearby.application.card.WalletRepository
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.ExchangeContext
import io.wid.dearby.domain.card.Receipt
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


    data class ContextJson(val activityId: Long?, val label: String?)
}
