package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.HISTORY_EVIDENCE
import io.wid.dearby.application.profile.EvidenceRepository
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.Evidence
import io.wid.dearby.domain.profile.EvidenceMeta
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.*

@Repository
class JooqEvidenceRepository(private val dsl: DSLContext) : EvidenceRepository {

    override fun add(historyId: UUID, userId: UserId, evidence: Evidence) {
        dsl.insertInto(HISTORY_EVIDENCE)
            .set(HISTORY_EVIDENCE.ID, evidence.meta.id)
            .set(HISTORY_EVIDENCE.HISTORY_ID, historyId)
            .set(HISTORY_EVIDENCE.USER_ID, userId)
            .set(HISTORY_EVIDENCE.FILE_NAME, evidence.meta.fileName)
            .set(HISTORY_EVIDENCE.CONTENT_TYPE, evidence.meta.contentType)
            .set(HISTORY_EVIDENCE.SIZE, evidence.meta.size)
            .set(HISTORY_EVIDENCE.DATA, evidence.data)
            .set(HISTORY_EVIDENCE.UPLOADED_AT, evidence.meta.uploadedAt)
            .execute()
    }

    override fun find(historyId: UUID, evidenceId: UUID): Evidence? =
        dsl.selectFrom(HISTORY_EVIDENCE).where(HISTORY_EVIDENCE.HISTORY_ID.eq(historyId), HISTORY_EVIDENCE.ID.eq(evidenceId)).fetchOne()
            ?.let { Evidence(EvidenceMeta(it.id!!, it.fileName!!, it.contentType!!, it.size!!, it.uploadedAt!!), it.data!!) }

    override fun delete(historyId: UUID, evidenceId: UUID): Boolean =
        dsl.deleteFrom(HISTORY_EVIDENCE).where(HISTORY_EVIDENCE.HISTORY_ID.eq(historyId), HISTORY_EVIDENCE.ID.eq(evidenceId)).execute() > 0

    override fun count(historyId: UUID): Int = dsl.fetchCount(HISTORY_EVIDENCE, HISTORY_EVIDENCE.HISTORY_ID.eq(historyId))
}
