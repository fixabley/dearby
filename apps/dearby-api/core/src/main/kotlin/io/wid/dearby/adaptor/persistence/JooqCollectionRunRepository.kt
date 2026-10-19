package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.COLLECTION_RUNS
import io.wid.dearby.application.collection.CollectionRunRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.collection.CollectionReport
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqCollectionRunRepository(private val dsl: DSLContext) : CollectionRunRepository {

    // unique(program_id, run_date)라 같은 날 두 번 시작하지 않는다(동시에 실행돼도)
    override fun start(programId: Id, runDate: LocalDate, officialOnly: Boolean): Id? =
        dsl.insertInto(COLLECTION_RUNS)
            .set(COLLECTION_RUNS.PROGRAM_ID, programId)
            .set(COLLECTION_RUNS.RUN_DATE, runDate)
            .set(COLLECTION_RUNS.OFFICIAL_ONLY, officialOnly)
            .set(COLLECTION_RUNS.STARTED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .onConflictDoNothing()
            .returning(COLLECTION_RUNS.ID)
            .fetchOne()
            ?.id

    override fun finish(runId: Id, report: CollectionReport) {
        dsl.update(COLLECTION_RUNS)
            .set(COLLECTION_RUNS.FINISHED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .set(COLLECTION_RUNS.OUTCOME, report.outcome.name)
            .set(COLLECTION_RUNS.FOUND_COUNT, report.foundCount)
            .set(COLLECTION_RUNS.SAVED_COUNT, report.savedCount)
            .set(COLLECTION_RUNS.REJECTED_REASONS, report.rejectedReasons.toTypedArray<String?>())
            .set(COLLECTION_RUNS.ERROR, report.error)
            .set(COLLECTION_RUNS.INPUT_TOKENS, report.inputTokens)
            .set(COLLECTION_RUNS.OUTPUT_TOKENS, report.outputTokens)
            .set(COLLECTION_RUNS.WEB_SEARCH_REQUESTS, report.webSearchRequests)
            .set(COLLECTION_RUNS.FETCHED_URLS, report.fetchedUrls.toTypedArray<String?>())
            .where(COLLECTION_RUNS.ID.eq(runId))
            .execute()
    }
}
