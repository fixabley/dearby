package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.CardBlocksRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.BUSINESS_CARDS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.BUSINESS_CARD_PHOTOS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.CARD_REPORT_PHOTOS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.CARD_BLOCKS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.CARD_REPORTS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.GUEST_SESSIONS
import io.wid.dearby.application.card.CardSafetyRepository
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.card.*
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.OffsetDateTime
import java.util.*

@Repository
class JooqCardSafetyRepository(private val dsl: DSLContext) : CardSafetyRepository {

    private val json = JsonMapper.builder().findAndAddModules().build()

    override fun createReport(report: CardReport) {
        dsl.insertInto(CARD_REPORTS)
            .set(CARD_REPORTS.ID, report.id)
            .set(CARD_REPORTS.CARD_ID, report.cardId)
            .set(CARD_REPORTS.REPORTER_USER_ID, report.reporterUserId)
            .set(CARD_REPORTS.REPORTER_GUEST_DIGEST, report.reporterGuestDigest)
            .set(CARD_REPORTS.REASON, report.reason.name)
            .set(CARD_REPORTS.DETAIL, report.detail)
            .set(CARD_REPORTS.CARD_SNAPSHOT, JSONB.valueOf(json.writeValueAsString(report.snapshot)))
            .set(CARD_REPORTS.STATUS, report.status.name)
            .set(CARD_REPORTS.CREATED_AT, report.createdAt)
            .execute()
    }

    override fun copyCardPhoto(reportId: UUID, cardId: UUID) {
        dsl.insertInto(CARD_REPORT_PHOTOS, CARD_REPORT_PHOTOS.REPORT_ID, CARD_REPORT_PHOTOS.DATA)
            .select(dsl.select(DSL.`val`(reportId), BUSINESS_CARD_PHOTOS.DATA).from(BUSINESS_CARD_PHOTOS).where(BUSINESS_CARD_PHOTOS.CARD_ID.eq(cardId)))
            .execute()
    }

    override fun reportPhoto(reportId: UUID): ByteArray? =
        dsl.select(CARD_REPORT_PHOTOS.DATA).from(CARD_REPORT_PHOTOS).where(CARD_REPORT_PHOTOS.REPORT_ID.eq(reportId)).fetchOne(CARD_REPORT_PHOTOS.DATA)

    override fun recentReport(cardId: UUID, reporterUserId: UserId?, reporterGuestDigest: String?, since: OffsetDateTime): CardReport? {
        val reporter = when {
            reporterUserId != null -> CARD_REPORTS.REPORTER_USER_ID.eq(reporterUserId)
            reporterGuestDigest != null -> CARD_REPORTS.REPORTER_GUEST_DIGEST.eq(reporterGuestDigest)
            else -> return null
        }
        return dsl.selectFrom(CARD_REPORTS).where(CARD_REPORTS.CARD_ID.eq(cardId), reporter, CARD_REPORTS.CREATED_AT.gt(since))
            .orderBy(CARD_REPORTS.CREATED_AT.desc()).limit(1).fetchOne()?.let(::toReport)
    }

    private val reportCount = DSL.count().over(DSL.partitionBy(CARD_REPORTS.CARD_ID)).`as`("report_count")
    private val withdrawn = DSL.notExists(DSL.selectOne().from(BUSINESS_CARDS).where(BUSINESS_CARDS.ID.eq(CARD_REPORTS.CARD_ID), BUSINESS_CARDS.REVOKED.isFalse)).`as`("card_withdrawn")

    // 같은 명함 신고 수는 상태와 관계없이 센다(창 함수가 바깥 where 전에 계산되도록 파생 테이블로 감싼다)
    private fun allReports() = dsl.select(*CARD_REPORTS.fields(), reportCount, withdrawn).from(CARD_REPORTS).asTable("all_reports")

    override fun reports(status: ReportStatus?): List<CardReportListing> {
        val all = allReports()
        val rows = dsl.selectFrom(all)
            .where(status?.let { all.field(CARD_REPORTS.STATUS)!!.eq(it.name) } ?: DSL.noCondition())
            .orderBy(DSL.`when`(all.field(CARD_REPORTS.STATUS)!!.eq(ReportStatus.OPEN.name), 0).otherwise(1), all.field(CARD_REPORTS.CREATED_AT)!!.desc())
            .fetch()
        return rows.map { listing(it.into(CARD_REPORTS).let(::toReport), it) }
    }

    override fun findReport(id: UUID): CardReportListing? {
        val all = allReports()
        return dsl.selectFrom(all).where(all.field(CARD_REPORTS.ID)!!.eq(id)).fetchOne()?.let { listing(it.into(CARD_REPORTS).let(::toReport), it) }
    }

    private fun listing(report: CardReport, row: Record) =
        CardReportListing(report, row.get("report_count", Int::class.java)!!, row.get("card_withdrawn", Boolean::class.java)!!)

    override fun updateReport(id: UUID, status: ReportStatus, note: String?, by: UserId?, now: OffsetDateTime): Boolean {
        val open = status == ReportStatus.OPEN
        return dsl.update(CARD_REPORTS)
            .set(CARD_REPORTS.STATUS, status.name)
            .set(CARD_REPORTS.NOTE, note)
            .set(CARD_REPORTS.RESOLVED_AT, if (open) null else now)
            .set(CARD_REPORTS.RESOLVED_BY, if (open) null else by)
            .where(CARD_REPORTS.ID.eq(id)).execute() > 0
    }

    override fun resolveOpen(cardId: UUID, note: String, by: UserId?, now: OffsetDateTime) {
        dsl.update(CARD_REPORTS)
            .set(CARD_REPORTS.STATUS, ReportStatus.RESOLVED.name).set(CARD_REPORTS.NOTE, note)
            .set(CARD_REPORTS.RESOLVED_AT, now).set(CARD_REPORTS.RESOLVED_BY, by)
            .where(CARD_REPORTS.CARD_ID.eq(cardId), CARD_REPORTS.STATUS.eq(ReportStatus.OPEN.name)).execute()
    }

    override fun deleteReportsBefore(at: OffsetDateTime) =
        dsl.deleteFrom(CARD_REPORTS).where(CARD_REPORTS.CREATED_AT.lt(at)).execute()

    override fun deleteGuestBlocksBefore(at: OffsetDateTime) =
        dsl.deleteFrom(CARD_BLOCKS).where(
            CARD_BLOCKS.BLOCKER_GUEST_DIGEST.isNotNull,
            CARD_BLOCKS.CREATED_AT.lt(at).or(CARD_BLOCKS.BLOCKER_GUEST_DIGEST.notIn(dsl.select(GUEST_SESSIONS.DIGEST).from(GUEST_SESSIONS))),
        ).execute()

    private fun blocker(user: UserId?, guest: String?): Condition =
        if (user != null) CARD_BLOCKS.BLOCKER_USER_ID.eq(user) else CARD_BLOCKS.BLOCKER_GUEST_DIGEST.eq(guest)

    override fun block(blockerUser: UserId?, blockerGuest: String?, blockedUser: UserId, name: String, now: OffsetDateTime): CardBlock {
        dsl.selectFrom(CARD_BLOCKS).where(blocker(blockerUser, blockerGuest), CARD_BLOCKS.BLOCKED_USER_ID.eq(blockedUser)).fetchOne()?.let { return toBlock(it) }
        val id = UUID.randomUUID()
        dsl.insertInto(CARD_BLOCKS)
            .set(CARD_BLOCKS.ID, id).set(CARD_BLOCKS.BLOCKER_USER_ID, blockerUser).set(CARD_BLOCKS.BLOCKER_GUEST_DIGEST, blockerGuest)
            .set(CARD_BLOCKS.BLOCKED_USER_ID, blockedUser).set(CARD_BLOCKS.NAME, name).set(CARD_BLOCKS.CREATED_AT, now)
            .execute()
        return CardBlock(id, name, now)
    }

    override fun isBlocked(blockerUser: UserId?, blockerGuest: String?, blockedUser: UserId) =
        (blockerUser != null || blockerGuest != null) && dsl.fetchExists(CARD_BLOCKS, blocker(blockerUser, blockerGuest), CARD_BLOCKS.BLOCKED_USER_ID.eq(blockedUser))

    override fun blocks(blockerUser: UserId) =
        dsl.selectFrom(CARD_BLOCKS).where(CARD_BLOCKS.BLOCKER_USER_ID.eq(blockerUser)).orderBy(CARD_BLOCKS.CREATED_AT.desc()).fetch().map(::toBlock)

    override fun unblock(blockerUser: UserId, id: UUID) =
        dsl.deleteFrom(CARD_BLOCKS).where(CARD_BLOCKS.ID.eq(id), CARD_BLOCKS.BLOCKER_USER_ID.eq(blockerUser)).execute() > 0

    private fun toBlock(it: CardBlocksRecord) = CardBlock(it.id!!, it.name!!, it.createdAt!!)

    private fun toReport(it: io.wid.dearby.adaptor.persistence.jooq.tables.records.CardReportsRecord) = CardReport(
        id = it.id!!,
        cardId = it.cardId!!,
        reporterUserId = it.reporterUserId,
        reporterGuestDigest = it.reporterGuestDigest,
        reason = ReportReason.valueOf(it.reason!!),
        detail = it.detail,
        snapshot = json.readValue<CardSnapshot>(it.cardSnapshot!!.data()),
        status = ReportStatus.valueOf(it.status!!),
        createdAt = it.createdAt!!,
        note = it.note,
    )
}
