package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.CONTACTS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROFILES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROFILE_HISTORIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROFILE_JOBS
import io.wid.dearby.application.profile.ProfileRepository
import io.wid.dearby.domain.ContactKind
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.Contact
import io.wid.dearby.domain.profile.History
import io.wid.dearby.domain.profile.Profile
import io.wid.dearby.domain.profile.ProfileJob
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

@Repository
class JooqProfileRepository(private val dsl: DSLContext) : ProfileRepository {

    private val histories = HistoryRows(dsl)
    private val jobs = ProfileJobRows(dsl)

    override fun find(userId: UserId): Profile? {
        val row = dsl.selectFrom(PROFILES).where(PROFILES.USER_ID.eq(userId)).fetchOne() ?: return null
        val contacts = dsl.selectFrom(CONTACTS).where(CONTACTS.USER_ID.eq(userId)).orderBy(CONTACTS.POSITION).fetch()
            .map { Contact(it.id!!, ContactKind.valueOf(it.kind!!), it.label!!, it.value!!, it.verified!!, it.verifiedAt) }
        val historyRows = dsl.selectFrom(PROFILE_HISTORIES).where(PROFILE_HISTORIES.USER_ID.eq(userId)).orderBy(PROFILE_HISTORIES.POSITION).fetch()
        val jobRows = dsl.selectFrom(PROFILE_JOBS).where(PROFILE_JOBS.USER_ID.eq(userId)).orderBy(PROFILE_JOBS.POSITION).fetch()
        return Profile(userId, row.name!!, jobs.load(jobRows), row.introLink, contacts, histories.load(historyRows), row.version!!, row.updatedAt!!)
    }

    // 연락처는 전부 다시 넣는다(명함은 복사본이라 영향 없음). 이력은 id가 같으면 고쳐서 인증자료(FK)와 상태를 지키고, 빠진 것은 지운다
    @Transactional
    override fun save(profile: Profile) {
        val userId = profile.userId
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        dsl.insertInto(PROFILES)
            .set(PROFILES.USER_ID, userId)
            .set(PROFILES.NAME, profile.name)
            .set(PROFILES.INTRODUCTION, "") // 소개 글 폐지(2026-10-26): 새 행만 빈 값, 기존 값은 건드리지 않는다
            .set(PROFILES.INTRO_LINK, profile.introLink)
            .set(PROFILES.VERSION, profile.version)
            .set(PROFILES.CREATED_AT, now)
            .set(PROFILES.UPDATED_AT, profile.updatedAt)
            .onConflict(PROFILES.USER_ID).doUpdate()
            .set(PROFILES.NAME, profile.name)
            .set(PROFILES.INTRO_LINK, profile.introLink)
            .set(PROFILES.VERSION, profile.version)
            .set(PROFILES.UPDATED_AT, profile.updatedAt)
            .execute()
        dsl.deleteFrom(CONTACTS).where(CONTACTS.USER_ID.eq(userId)).execute()
        profile.contacts.forEachIndexed { index, contact ->
            dsl.insertInto(CONTACTS)
                .set(CONTACTS.ID, contact.id)
                .set(CONTACTS.USER_ID, userId)
                .set(CONTACTS.KIND, contact.kind.name)
                .set(CONTACTS.LABEL, contact.label)
                .set(CONTACTS.VALUE, contact.value)
                .set(CONTACTS.VERIFIED, contact.verified)
                .set(CONTACTS.VERIFIED_AT, contact.verifiedAt)
                .set(CONTACTS.POSITION, index)
                .execute()
        }
        val keep = profile.histories.map { it.id }
        dsl.deleteFrom(PROFILE_HISTORIES).where(PROFILE_HISTORIES.USER_ID.eq(userId), if (keep.isEmpty()) org.jooq.impl.DSL.noCondition() else PROFILE_HISTORIES.ID.notIn(keep)).execute()
        profile.histories.forEachIndexed { index, history -> upsert(userId, history, index, now) }
        // 직무 태그도 이력처럼 id가 같으면 고쳐서 승인 상태를 지키고, 빠진 것은 지운다
        val keepJobs = profile.jobs.map { it.id }
        dsl.deleteFrom(PROFILE_JOBS).where(PROFILE_JOBS.USER_ID.eq(userId), if (keepJobs.isEmpty()) org.jooq.impl.DSL.noCondition() else PROFILE_JOBS.ID.notIn(keepJobs)).execute()
        profile.jobs.forEachIndexed { index, job -> upsertJob(userId, job, index, now) }
    }

    private fun upsertJob(userId: UserId, j: ProfileJob, position: Int, now: OffsetDateTime) {
        dsl.insertInto(PROFILE_JOBS)
            .set(PROFILE_JOBS.ID, j.id)
            .set(PROFILE_JOBS.USER_ID, userId)
            .set(PROFILE_JOBS.JOB_ID, j.job?.id)
            .set(PROFILE_JOBS.JOB_NAME, j.jobName.takeIf { j.job == null })
            .set(PROFILE_JOBS.POSITION, position)
            .set(PROFILE_JOBS.APPROVAL_STATUS, j.approval.name)
            .set(PROFILE_JOBS.APPROVAL_REASON, j.approvalReason)
            .set(PROFILE_JOBS.CREATED_AT, now)
            .set(PROFILE_JOBS.UPDATED_AT, now)
            .onConflict(PROFILE_JOBS.ID).doUpdate()
            .set(PROFILE_JOBS.JOB_ID, j.job?.id)
            .set(PROFILE_JOBS.JOB_NAME, j.jobName.takeIf { j.job == null })
            .set(PROFILE_JOBS.POSITION, position)
            .set(PROFILE_JOBS.APPROVAL_STATUS, j.approval.name)
            .set(PROFILE_JOBS.APPROVAL_REASON, j.approvalReason)
            .set(PROFILE_JOBS.UPDATED_AT, now)
            .where(PROFILE_JOBS.USER_ID.eq(userId))
            .execute()
    }

    private fun upsert(userId: UserId, h: History, position: Int, now: OffsetDateTime) {
        dsl.insertInto(PROFILE_HISTORIES)
            .set(PROFILE_HISTORIES.ID, h.id)
            .set(PROFILE_HISTORIES.USER_ID, userId)
            .set(PROFILE_HISTORIES.KIND, h.kind.name)
            .set(PROFILE_HISTORIES.ORGANIZATION_ID, h.organization?.id)
            .set(PROFILE_HISTORIES.ORGANIZATION_NAME, h.organizationName.takeIf { h.organization == null })
            .set(PROFILE_HISTORIES.PROGRAM_ID, h.program?.id)
            .set(PROFILE_HISTORIES.PROGRAM_NAME, h.programName.takeIf { h.program == null })
            .set(PROFILE_HISTORIES.ACTIVITY_ID, h.activity?.id)
            .set(PROFILE_HISTORIES.ACTIVITY_NAME, h.activityName.takeIf { h.activity == null })
            .set(PROFILE_HISTORIES.ROLE_ID, h.role?.id)
            .set(PROFILE_HISTORIES.ROLE_NAME, h.roleName.takeIf { h.role == null })
            .set(PROFILE_HISTORIES.START_DATE, h.startDate)
            .set(PROFILE_HISTORIES.END_DATE, h.endDate)
            .set(PROFILE_HISTORIES.DESCRIPTION, h.description)
            .set(PROFILE_HISTORIES.POSITION, position)
            .set(PROFILE_HISTORIES.APPROVAL_STATUS, h.approval.name)
            .set(PROFILE_HISTORIES.APPROVAL_REASON, h.approvalReason)
            .set(PROFILE_HISTORIES.VERIFICATION_STATUS, h.verification.name)
            .set(PROFILE_HISTORIES.CREATED_AT, now)
            .set(PROFILE_HISTORIES.UPDATED_AT, now)
            .onConflict(PROFILE_HISTORIES.ID).doUpdate()
            .set(PROFILE_HISTORIES.KIND, h.kind.name)
            .set(PROFILE_HISTORIES.ORGANIZATION_ID, h.organization?.id)
            .set(PROFILE_HISTORIES.ORGANIZATION_NAME, h.organizationName.takeIf { h.organization == null })
            .set(PROFILE_HISTORIES.PROGRAM_ID, h.program?.id)
            .set(PROFILE_HISTORIES.PROGRAM_NAME, h.programName.takeIf { h.program == null })
            .set(PROFILE_HISTORIES.ACTIVITY_ID, h.activity?.id)
            .set(PROFILE_HISTORIES.ACTIVITY_NAME, h.activityName.takeIf { h.activity == null })
            .set(PROFILE_HISTORIES.ROLE_ID, h.role?.id)
            .set(PROFILE_HISTORIES.ROLE_NAME, h.roleName.takeIf { h.role == null })
            .set(PROFILE_HISTORIES.START_DATE, h.startDate)
            .set(PROFILE_HISTORIES.END_DATE, h.endDate)
            .set(PROFILE_HISTORIES.DESCRIPTION, h.description)
            .set(PROFILE_HISTORIES.POSITION, position)
            .set(PROFILE_HISTORIES.APPROVAL_STATUS, h.approval.name)
            .set(PROFILE_HISTORIES.APPROVAL_REASON, h.approvalReason)
            .set(PROFILE_HISTORIES.VERIFICATION_STATUS, h.verification.name)
            .set(PROFILE_HISTORIES.UPDATED_AT, now)
            // 다른 사용자의 이력 id와 겹치면 바꾸지 않는다(서비스가 400으로 거른다)
            .where(PROFILE_HISTORIES.USER_ID.eq(userId))
            .execute()
    }

    override fun findHistory(userId: UserId, historyId: UUID): History? =
        dsl.selectFrom(PROFILE_HISTORIES).where(PROFILE_HISTORIES.USER_ID.eq(userId), PROFILE_HISTORIES.ID.eq(historyId)).fetch()
            .let { histories.load(it) }.singleOrNull()
}
