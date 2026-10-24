package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAM_COVERS
import io.wid.dearby.application.program.ProgramCoverRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ProgramCover
import io.wid.dearby.domain.ProgramCoverInfo
import org.jooq.DSLContext
import org.jooq.Record
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqProgramCoverRepository(private val dsl: DSLContext) : ProgramCoverRepository {

    private val infoFields = listOf(PROGRAM_COVERS.PROGRAM_ID, PROGRAM_COVERS.SOURCE_PAGE, PROGRAM_COVERS.SOURCE_IMAGE, PROGRAM_COVERS.ENABLED, PROGRAM_COVERS.UPDATED_AT)

    private fun info(row: Record) = ProgramCoverInfo(
        row.get(PROGRAM_COVERS.PROGRAM_ID)!!, row.get(PROGRAM_COVERS.SOURCE_PAGE), row.get(PROGRAM_COVERS.SOURCE_IMAGE),
        row.get(PROGRAM_COVERS.ENABLED)!!, row.get(PROGRAM_COVERS.UPDATED_AT)!!,
    )

    override fun find(programId: Id): ProgramCover? =
        dsl.selectFrom(PROGRAM_COVERS).where(PROGRAM_COVERS.PROGRAM_ID.eq(programId)).fetchOne()?.let { ProgramCover(info(it), it.contentType!!, it.data!!) }

    override fun infoByProgram(programIds: Collection<Id>): Map<Id, ProgramCoverInfo> {
        if (programIds.isEmpty()) return emptyMap()
        return dsl.select(infoFields).from(PROGRAM_COVERS).where(PROGRAM_COVERS.PROGRAM_ID.`in`(programIds)).fetch().map(::info).associateBy { it.programId }
    }

    override fun save(cover: ProgramCover) {
        val record = dsl.newRecord(PROGRAM_COVERS).apply {
            programId = cover.info.programId
            contentType = cover.contentType
            data = cover.data
            sourcePage = cover.info.sourcePage
            sourceImage = cover.info.sourceImage
            enabled = cover.info.enabled
            updatedAt = cover.info.updatedAt
        }
        dsl.insertInto(PROGRAM_COVERS).set(record).onConflict(PROGRAM_COVERS.PROGRAM_ID).doUpdate().set(record).execute()
    }

    override fun setEnabled(programId: Id, enabled: Boolean): Boolean =
        dsl.update(PROGRAM_COVERS)
            .set(PROGRAM_COVERS.ENABLED, enabled)
            .set(PROGRAM_COVERS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(PROGRAM_COVERS.PROGRAM_ID.eq(programId))
            .execute() > 0

    override fun delete(programId: Id) {
        dsl.deleteFrom(PROGRAM_COVERS).where(PROGRAM_COVERS.PROGRAM_ID.eq(programId)).execute()
    }
}
