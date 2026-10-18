package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.COLLECTION_WATCHLIST
import io.wid.dearby.application.collection.WatchlistRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.collection.WatchEntry
import io.wid.dearby.domain.collection.WatchKind
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqWatchlistRepository(private val dsl: DSLContext) : WatchlistRepository {

    private val w = COLLECTION_WATCHLIST
    private val canonical = ACTIVITIES.`as`("canonical")

    override fun findAllByProgramId(programId: Id): List<WatchEntry> = select().where(w.PROGRAM_ID.eq(programId)).orderBy(w.ID).fetch().map(::toEntry)

    override fun findById(id: Id): WatchEntry? = select().where(w.ID.eq(id)).fetchOne()?.let(::toEntry)

    override fun create(programId: Id, name: String, kind: WatchKind, canonicalActivityId: Id?, reason: String?): WatchEntry {
        val id = dsl.insertInto(w)
            .set(w.PROGRAM_ID, programId)
            .set(w.NAME, name)
            .set(w.NORMALIZED_NAME, WatchEntry.normalize(name))
            .set(w.KIND, kind.name)
            .set(w.CANONICAL_ACTIVITY_ID, canonicalActivityId)
            .set(w.REASON, reason)
            .set(w.CREATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .returning(w.ID)
            .fetchOne()!!.id!!
        return findById(id)!!
    }

    override fun delete(id: Id): Boolean = dsl.deleteFrom(w).where(w.ID.eq(id)).execute() > 0

    private fun select() = dsl.select(w.asterisk(), canonical.NAME, canonical.SOURCE_ID)
        .from(w)
        .leftJoin(canonical).on(canonical.ID.eq(w.CANONICAL_ACTIVITY_ID))

    private fun toEntry(row: Record) = WatchEntry(
        id = row[w.ID]!!,
        programId = row[w.PROGRAM_ID]!!,
        name = row[w.NAME]!!,
        normalizedName = row[w.NORMALIZED_NAME]!!,
        kind = WatchKind.valueOf(row[w.KIND]!!),
        canonicalActivityId = row[w.CANONICAL_ACTIVITY_ID],
        canonicalActivityName = row[canonical.NAME],
        canonicalSourceId = row[canonical.SOURCE_ID],
        reason = row[w.REASON],
        createdAt = row[w.CREATED_AT]!!,
    )
}
