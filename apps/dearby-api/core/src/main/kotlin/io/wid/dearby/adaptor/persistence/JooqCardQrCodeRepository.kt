package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.CARD_QR_CODES
import io.wid.dearby.application.card.CardQrCodeRepository
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.*

@Repository
class JooqCardQrCodeRepository(private val dsl: DSLContext) : CardQrCodeRepository {

    override fun create(id: UUID, cardId: UUID, codeHash: String, createdAt: OffsetDateTime, expiresAt: OffsetDateTime) {
        dsl.insertInto(CARD_QR_CODES)
            .set(CARD_QR_CODES.ID, id)
            .set(CARD_QR_CODES.CARD_ID, cardId)
            .set(CARD_QR_CODES.CODE_HASH, codeHash)
            .set(CARD_QR_CODES.CREATED_AT, createdAt)
            .set(CARD_QR_CODES.EXPIRES_AT, expiresAt)
            .execute()
    }

    override fun shorten(cardId: UUID, id: UUID, latest: OffsetDateTime) {
        dsl.update(CARD_QR_CODES)
            .set(CARD_QR_CODES.EXPIRES_AT, DSL.least(CARD_QR_CODES.EXPIRES_AT, DSL.`val`(latest)))
            .where(CARD_QR_CODES.ID.eq(id), CARD_QR_CODES.CARD_ID.eq(cardId))
            .execute()
    }

    override fun revokeAll(cardId: UUID, now: OffsetDateTime) {
        dsl.update(CARD_QR_CODES).set(CARD_QR_CODES.REVOKED_AT, now)
            .where(CARD_QR_CODES.CARD_ID.eq(cardId), CARD_QR_CODES.REVOKED_AT.isNull)
            .execute()
    }

    override fun isValid(cardId: UUID, codeHash: String, now: OffsetDateTime) = dsl.fetchExists(
        CARD_QR_CODES,
        CARD_QR_CODES.CARD_ID.eq(cardId), CARD_QR_CODES.CODE_HASH.eq(codeHash), CARD_QR_CODES.REVOKED_AT.isNull, CARD_QR_CODES.EXPIRES_AT.gt(now),
    )
}
