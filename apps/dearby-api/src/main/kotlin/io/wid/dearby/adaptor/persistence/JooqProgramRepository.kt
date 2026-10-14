package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAMS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAM_CONTACTS
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.*
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class JooqProgramRepository(private val dsl: DSLContext) : ProgramRepository {

    override fun findAllById(ids: Collection<Id>): List<Program> {
        if (ids.isEmpty()) return emptyList()
        val contacts = dsl.selectFrom(PROGRAM_CONTACTS)
            .where(PROGRAM_CONTACTS.PROGRAM_ID.`in`(ids))
            .orderBy(PROGRAM_CONTACTS.ID)
            .fetch()
            .groupBy({ it.programId!! }) {
                ProgramContact(it.id!!, it.programId!!, ContactKind.valueOf(it.kind!!), it.label!!, it.value!!)
            }
        return dsl.selectFrom(PROGRAMS).where(PROGRAMS.ID.`in`(ids)).fetch().map {
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
