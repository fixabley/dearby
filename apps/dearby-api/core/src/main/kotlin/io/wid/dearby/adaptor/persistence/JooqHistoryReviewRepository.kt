package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROFILES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROFILE_HISTORIES
import io.wid.dearby.application.profile.HistoryForReview
import io.wid.dearby.application.profile.HistoryReviewRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.profile.ApprovalStatus
import io.wid.dearby.domain.profile.VerificationStatus
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Repository
class JooqHistoryReviewRepository(private val dsl: DSLContext) : HistoryReviewRepository {

    private val rows = HistoryRows(dsl)

    override fun search(approval: ApprovalStatus?, verification: VerificationStatus?, after: UUID?, size: Int): List<HistoryForReview> {
        val conditions = buildList<Condition> {
            approval?.let { add(PROFILE_HISTORIES.APPROVAL_STATUS.eq(it.name)) }
            verification?.let { add(PROFILE_HISTORIES.VERIFICATION_STATUS.eq(it.name)) }
            after?.let { add(PROFILE_HISTORIES.ID.gt(it)) }
        }
        return load(dsl.selectFrom(PROFILE_HISTORIES).where(conditions).orderBy(PROFILE_HISTORIES.ID).limit(size).fetch())
    }

    override fun find(historyId: UUID): HistoryForReview? =
        load(dsl.selectFrom(PROFILE_HISTORIES).where(PROFILE_HISTORIES.ID.eq(historyId)).fetch()).singleOrNull()

    // 준 id는 직접 입력 값을 대신한다(이름은 지운다)
    override fun updateApproval(historyId: UUID, status: ApprovalStatus, reason: String?, organizationId: Id?, programId: Id?, activityId: Id?, roleId: Id?): Boolean {
        var update = dsl.update(PROFILE_HISTORIES)
            .set(PROFILE_HISTORIES.APPROVAL_STATUS, status.name)
            .set(PROFILE_HISTORIES.APPROVAL_REASON, reason)
            .set(PROFILE_HISTORIES.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
        organizationId?.let { update = update.set(PROFILE_HISTORIES.ORGANIZATION_ID, it).setNull(PROFILE_HISTORIES.ORGANIZATION_NAME) }
        programId?.let { update = update.set(PROFILE_HISTORIES.PROGRAM_ID, it).setNull(PROFILE_HISTORIES.PROGRAM_NAME) }
        activityId?.let { update = update.set(PROFILE_HISTORIES.ACTIVITY_ID, it).setNull(PROFILE_HISTORIES.ACTIVITY_NAME) }
        roleId?.let { update = update.set(PROFILE_HISTORIES.ROLE_ID, it).setNull(PROFILE_HISTORIES.ROLE_NAME) }
        return update.where(PROFILE_HISTORIES.ID.eq(historyId)).execute() > 0
    }

    override fun updateVerification(historyId: UUID, status: VerificationStatus): Boolean =
        dsl.update(PROFILE_HISTORIES)
            .set(PROFILE_HISTORIES.VERIFICATION_STATUS, status.name)
            .set(PROFILE_HISTORIES.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(PROFILE_HISTORIES.ID.eq(historyId))
            .execute() > 0

    private fun load(records: org.jooq.Result<io.wid.dearby.adaptor.persistence.jooq.tables.records.ProfileHistoriesRecord>): List<HistoryForReview> {
        if (records.isEmpty()) return emptyList()
        val userIds = records.map { it.userId!! }.toSet()
        val names = dsl.select(PROFILES.USER_ID, PROFILES.NAME).from(PROFILES).where(PROFILES.USER_ID.`in`(userIds)).fetch().associate { it.value1()!! to it.value2()!! }
        val histories = rows.load(records)
        return records.zip(histories) { record, history -> HistoryForReview(history, record.userId!!, names[record.userId] ?: "") }
    }
}
