package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROFILES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROFILE_JOBS
import io.wid.dearby.application.profile.ProfileJobForReview
import io.wid.dearby.application.profile.ProfileJobReviewRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.profile.ApprovalStatus
import org.jooq.Condition
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Repository
class JooqProfileJobReviewRepository(private val dsl: DSLContext) : ProfileJobReviewRepository {

    private val rows = ProfileJobRows(dsl)

    // 직접 입력(job_id 없음)만 검토 대상이다
    override fun search(approval: ApprovalStatus?, after: UUID?, size: Int): List<ProfileJobForReview> {
        val conditions = buildList<Condition> {
            add(PROFILE_JOBS.JOB_ID.isNull)
            approval?.let { add(PROFILE_JOBS.APPROVAL_STATUS.eq(it.name)) }
            after?.let { add(PROFILE_JOBS.ID.gt(it)) }
        }
        return load(dsl.selectFrom(PROFILE_JOBS).where(conditions).orderBy(PROFILE_JOBS.ID).limit(size).fetch())
    }

    override fun find(id: UUID): ProfileJobForReview? = load(dsl.selectFrom(PROFILE_JOBS).where(PROFILE_JOBS.ID.eq(id)).fetch()).singleOrNull()

    override fun updateApproval(id: UUID, status: ApprovalStatus, reason: String?, jobId: Id?): Boolean {
        var update = dsl.update(PROFILE_JOBS)
            .set(PROFILE_JOBS.APPROVAL_STATUS, status.name)
            .set(PROFILE_JOBS.APPROVAL_REASON, reason)
            .set(PROFILE_JOBS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
        jobId?.let { update = update.set(PROFILE_JOBS.JOB_ID, it).setNull(PROFILE_JOBS.JOB_NAME) }
        return update.where(PROFILE_JOBS.ID.eq(id)).execute() > 0
    }

    private fun load(records: org.jooq.Result<io.wid.dearby.adaptor.persistence.jooq.tables.records.ProfileJobsRecord>): List<ProfileJobForReview> {
        if (records.isEmpty()) return emptyList()
        val userIds = records.map { it.userId!! }.toSet()
        val names = dsl.select(PROFILES.USER_ID, PROFILES.NAME).from(PROFILES).where(PROFILES.USER_ID.`in`(userIds)).fetch().associate { it.value1()!! to it.value2()!! }
        return records.zip(rows.load(records)) { record, entry -> ProfileJobForReview(entry, record.userId!!, names[record.userId] ?: "") }
    }
}
