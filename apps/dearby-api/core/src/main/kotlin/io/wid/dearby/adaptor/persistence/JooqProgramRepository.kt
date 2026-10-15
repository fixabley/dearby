package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAMS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAM_CONTACTS
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.*
import io.wid.dearby.adaptor.persistence.jooq.tables.records.ProgramsRecord
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.Result
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.COLLECTION_RUNS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAM_COLLECTION_SETTINGS
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Repository
class JooqProgramRepository(private val dsl: DSLContext) : ProgramRepository {

    override fun findAllById(ids: Collection<Id>): List<Program> {
        if (ids.isEmpty()) return emptyList()
        return load(dsl.selectFrom(PROGRAMS).where(PROGRAMS.ID.`in`(ids)).fetch())
    }

    override fun search(q: String?, organizationId: Id?, after: Id?, size: Int): List<Program> {
        val conditions = buildList<Condition> {
            q?.takeIf { it.isNotBlank() }?.trim()?.let { add(PROGRAMS.NAME.containsIgnoreCase(it).or(PROGRAMS.NAME_EN.containsIgnoreCase(it))) }
            organizationId?.let { add(PROGRAMS.ORGANIZATION_ID.eq(it)) }
            after?.let { add(PROGRAMS.ID.gt(it)) }
        }
        return load(dsl.selectFrom(PROGRAMS).where(conditions).orderBy(PROGRAMS.ID).limit(size).fetch())
    }

    override fun create(organizationId: Id, name: String, nameEn: String?): Id {
        val now = LocalDateTime.now()
        return dsl.insertInto(PROGRAMS)
            .set(PROGRAMS.ORGANIZATION_ID, organizationId)
            .set(PROGRAMS.NAME, name)
            .set(PROGRAMS.NAME_EN, nameEn)
            .set(PROGRAMS.CREATED_AT, now)
            .set(PROGRAMS.UPDATED_AT, now)
            .returning(PROGRAMS.ID)
            .fetchOne()!!.id!!
    }

    override fun update(id: Id, organizationId: Id, name: String, nameEn: String?) {
        dsl.update(PROGRAMS)
            .set(PROGRAMS.ORGANIZATION_ID, organizationId)
            .set(PROGRAMS.NAME, name)
            .set(PROGRAMS.NAME_EN, nameEn)
            .set(PROGRAMS.UPDATED_AT, LocalDateTime.now())
            .where(PROGRAMS.ID.eq(id))
            .execute()
    }

    override fun existsName(organizationId: Id, name: String, exceptId: Id?, english: Boolean): Boolean =
        dsl.fetchExists(
            PROGRAMS,
            PROGRAMS.ORGANIZATION_ID.eq(organizationId),
            withoutSpaces(if (english) PROGRAMS.NAME_EN else PROGRAMS.NAME).eq(withoutSpaces(DSL.`val`(name))),
            exceptId?.let { PROGRAMS.ID.ne(it) } ?: DSL.noCondition(),
        )

    override fun activityCount(id: Id): Int = dsl.fetchCount(ACTIVITIES, ACTIVITIES.PROGRAM_ID.eq(id))

    @Transactional
    override fun delete(id: Id): Boolean {
        dsl.deleteFrom(PROGRAM_COLLECTION_SETTINGS).where(PROGRAM_COLLECTION_SETTINGS.PROGRAM_ID.eq(id)).execute()
        dsl.deleteFrom(COLLECTION_RUNS).where(COLLECTION_RUNS.PROGRAM_ID.eq(id)).execute()
        return dsl.deleteFrom(PROGRAMS).where(PROGRAMS.ID.eq(id)).execute() > 0
    }

    private fun load(rows: Result<ProgramsRecord>): List<Program> {
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it.id!! }
        val contacts = dsl.selectFrom(PROGRAM_CONTACTS)
            .where(PROGRAM_CONTACTS.PROGRAM_ID.`in`(ids))
            .orderBy(PROGRAM_CONTACTS.ID)
            .fetch()
            .groupBy({ it.programId!! }) {
                ProgramContact(it.id!!, it.programId!!, ContactKind.valueOf(it.kind!!), it.label!!, it.value!!)
            }
        return rows.map {
            Program(
                id = it.id!!,
                organizationId = it.organizationId!!,
                name = it.name!!,
                nameEn = it.nameEn,
                contacts = contacts[it.id].orEmpty(),
                period = Period(it.periodFrom, it.periodTo),
                timestamps = Timestamps(it.createdAt!!, it.updatedAt!!),
            )
        }
    }
}
