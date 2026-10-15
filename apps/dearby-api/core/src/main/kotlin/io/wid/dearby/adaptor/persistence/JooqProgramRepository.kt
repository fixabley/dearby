package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAMS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAM_CONTACTS
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.*
import io.wid.dearby.adaptor.persistence.jooq.tables.records.ProgramsRecord
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Result
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class JooqProgramRepository(private val dsl: DSLContext) : ProgramRepository {

    override fun findAllById(ids: Collection<Id>): List<Program> {
        if (ids.isEmpty()) return emptyList()
        return load(dsl.selectFrom(PROGRAMS).where(PROGRAMS.ID.`in`(ids)).fetch())
    }

    override fun search(q: String?, organizationId: Id?, after: Id?, size: Int): List<Program> {
        val conditions = buildList<Condition> {
            q?.takeIf { it.isNotBlank() }?.let { add(PROGRAMS.NAME.containsIgnoreCase(it.trim())) }
            organizationId?.let { add(PROGRAMS.ORGANIZATION_ID.eq(it)) }
            after?.let { add(PROGRAMS.ID.gt(it)) }
        }
        return load(dsl.selectFrom(PROGRAMS).where(conditions).orderBy(PROGRAMS.ID).limit(size).fetch())
    }

    override fun create(organizationId: Id, name: String): Id {
        val now = LocalDateTime.now()
        return dsl.insertInto(PROGRAMS)
            .set(PROGRAMS.ORGANIZATION_ID, organizationId)
            .set(PROGRAMS.NAME, name)
            .set(PROGRAMS.CREATED_AT, now)
            .set(PROGRAMS.UPDATED_AT, now)
            .returning(PROGRAMS.ID)
            .fetchOne()!!.id!!
    }

    override fun update(id: Id, organizationId: Id, name: String) {
        dsl.update(PROGRAMS)
            .set(PROGRAMS.ORGANIZATION_ID, organizationId)
            .set(PROGRAMS.NAME, name)
            .set(PROGRAMS.UPDATED_AT, LocalDateTime.now())
            .where(PROGRAMS.ID.eq(id))
            .execute()
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
                contacts = contacts[it.id].orEmpty(),
                period = Period(it.periodFrom, it.periodTo),
                timestamps = Timestamps(it.createdAt!!, it.updatedAt!!),
            )
        }
    }
}
