package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAM_SOURCES
import io.wid.dearby.application.collection.ProgramSourceRepository
import io.wid.dearby.domain.ProgramSource
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class JooqProgramSourceRepository(private val dsl: DSLContext) : ProgramSourceRepository {

    override fun findEnabled(): List<ProgramSource> =
        dsl.selectFrom(PROGRAM_SOURCES)
            .where(PROGRAM_SOURCES.ENABLED.isTrue)
            .orderBy(PROGRAM_SOURCES.ID)
            .fetch {
                ProgramSource(
                    id = it.id!!,
                    programId = it.programId!!,
                    officialUrl = it.officialUrl!!,
                    allowedHosts = it.allowedHosts!!.filterNotNull(),
                    enabled = it.enabled!!,
                )
            }
}
