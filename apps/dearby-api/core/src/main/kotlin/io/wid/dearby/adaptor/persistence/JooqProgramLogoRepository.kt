package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAM_LOGOS
import io.wid.dearby.application.program.ProgramLogoRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ProgramLogo
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class JooqProgramLogoRepository(private val dsl: DSLContext) : ProgramLogoRepository {

    override fun find(programId: Id): ProgramLogo? =
        dsl.selectFrom(PROGRAM_LOGOS).where(PROGRAM_LOGOS.PROGRAM_ID.eq(programId)).fetchOne()
            ?.let { ProgramLogo(it.programId!!, it.contentType!!, it.data!!, it.updatedAt!!) }

    override fun updatedAtByProgram(programIds: Collection<Id>): Map<Id, OffsetDateTime> {
        if (programIds.isEmpty()) return emptyMap()
        return dsl.select(PROGRAM_LOGOS.PROGRAM_ID, PROGRAM_LOGOS.UPDATED_AT)
            .from(PROGRAM_LOGOS)
            .where(PROGRAM_LOGOS.PROGRAM_ID.`in`(programIds))
            .fetch { it.value1()!! to it.value2()!! }
            .toMap()
    }

    override fun save(logo: ProgramLogo) {
        dsl.insertInto(PROGRAM_LOGOS)
            .set(PROGRAM_LOGOS.PROGRAM_ID, logo.programId)
            .set(PROGRAM_LOGOS.CONTENT_TYPE, logo.contentType)
            .set(PROGRAM_LOGOS.DATA, logo.data)
            .set(PROGRAM_LOGOS.UPDATED_AT, logo.updatedAt)
            .onConflict(PROGRAM_LOGOS.PROGRAM_ID)
            .doUpdate()
            .set(PROGRAM_LOGOS.CONTENT_TYPE, logo.contentType)
            .set(PROGRAM_LOGOS.DATA, logo.data)
            .set(PROGRAM_LOGOS.UPDATED_AT, logo.updatedAt)
            .execute()
    }

    override fun delete(programId: Id) {
        dsl.deleteFrom(PROGRAM_LOGOS).where(PROGRAM_LOGOS.PROGRAM_ID.eq(programId)).execute()
    }
}
