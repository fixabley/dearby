package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.CardSharesRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.CARD_SHARES
import io.wid.dearby.application.card.ShareRepository
import io.wid.dearby.domain.card.CardShare
import io.wid.dearby.domain.card.SharedActivity
import org.jooq.DSLContext
import org.jooq.JSONB
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class JooqShareRepository(private val dsl: DSLContext) : ShareRepository {

    override fun create(share: CardShare) {
        dsl.insertInto(CARD_SHARES)
            .set(CARD_SHARES.ID, share.id)
            .set(CARD_SHARES.CARD_ID, share.cardId)
            .set(CARD_SHARES.ACTIVITIES, JSONB.valueOf(ProfileJson.write(share.activities.map { ActivityJson(it.id, it.title) })))
            .set(CARD_SHARES.CREATED_AT, share.createdAt)
            .execute()
    }

    override fun find(id: UUID): CardShare? = dsl.selectFrom(CARD_SHARES).where(CARD_SHARES.ID.eq(id)).fetchOne()?.let(::toShare)

    override fun findAllById(ids: Collection<UUID>): List<CardShare> {
        if (ids.isEmpty()) return emptyList()
        return dsl.selectFrom(CARD_SHARES).where(CARD_SHARES.ID.`in`(ids)).fetch().map(::toShare)
    }

    private fun toShare(it: CardSharesRecord) = CardShare(it.id!!, it.cardId!!, readActivities(it.activities!!.data()), it.createdAt!!)

    data class ActivityJson(val id: Long, val title: String)

    companion object {
        fun readActivities(value: String): List<SharedActivity> = ProfileJson.read<List<ActivityJson>>(value).map { SharedActivity(it.id, it.title) }
    }
}
