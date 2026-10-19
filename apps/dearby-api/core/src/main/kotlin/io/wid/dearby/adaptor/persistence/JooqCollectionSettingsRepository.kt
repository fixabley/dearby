package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.COLLECTION_RUNS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAM_COLLECTION_SETTINGS
import io.wid.dearby.application.collection.CollectionSettingsRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.collection.CollectionOutcome
import io.wid.dearby.domain.curation.CollectionSettings
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.LocalDateTime

@Repository
class JooqCollectionSettingsRepository(private val dsl: DSLContext) : CollectionSettingsRepository {

    override fun findAllByProgramId(programIds: Collection<Id>): Map<Id, CollectionSettings> {
        if (programIds.isEmpty()) return emptyMap()
        return dsl.selectFrom(PROGRAM_COLLECTION_SETTINGS)
            .where(PROGRAM_COLLECTION_SETTINGS.PROGRAM_ID.`in`(programIds))
            .fetch()
            .associate {
                it.programId!! to CollectionSettings(
                    officialUrl = it.officialUrl,
                    allowedHosts = it.allowedHosts!!.filterNotNull(),
                    intervalDays = it.intervalDays!!,
                    enabled = it.enabled!!,
                )
            }
    }

    override fun save(programId: Id, settings: CollectionSettings) {
        val now = LocalDateTime.now()
        val table = PROGRAM_COLLECTION_SETTINGS
        dsl.insertInto(table)
            .set(table.PROGRAM_ID, programId)
            .set(table.OFFICIAL_URL, settings.officialUrl)
            .set(table.ALLOWED_HOSTS, settings.allowedHosts.toTypedArray<String?>())
            .set(table.INTERVAL_DAYS, settings.intervalDays)
            .set(table.ENABLED, settings.enabled)
            .set(table.CREATED_AT, now)
            .set(table.UPDATED_AT, now)
            .onConflict(table.PROGRAM_ID)
            .doUpdate()
            .set(table.OFFICIAL_URL, settings.officialUrl)
            .set(table.ALLOWED_HOSTS, settings.allowedHosts.toTypedArray<String?>())
            .set(table.INTERVAL_DAYS, settings.intervalDays)
            .set(table.ENABLED, settings.enabled)
            .set(table.UPDATED_AT, now)
            .execute()
    }

    override fun lastSucceededOn(programIds: Collection<Id>): Map<Id, LocalDate> {
        if (programIds.isEmpty()) return emptyMap()
        return dsl.select(COLLECTION_RUNS.PROGRAM_ID, DSL.max(COLLECTION_RUNS.RUN_DATE))
            .from(COLLECTION_RUNS)
            .where(COLLECTION_RUNS.PROGRAM_ID.`in`(programIds), COLLECTION_RUNS.OUTCOME.eq(CollectionOutcome.SUCCEEDED.name))
            .groupBy(COLLECTION_RUNS.PROGRAM_ID)
            .fetch()
            .associate { it.value1()!! to it.value2()!! }
    }

    override fun lastFetchedUrls(programIds: Collection<Id>): Map<Id, List<String>> {
        if (programIds.isEmpty()) return emptyMap()
        return dsl.select(COLLECTION_RUNS.PROGRAM_ID, COLLECTION_RUNS.FETCHED_URLS)
            .from(COLLECTION_RUNS)
            .where(COLLECTION_RUNS.PROGRAM_ID.`in`(programIds), COLLECTION_RUNS.OUTCOME.eq(CollectionOutcome.SUCCEEDED.name))
            .orderBy(COLLECTION_RUNS.RUN_DATE.desc(), COLLECTION_RUNS.ID.desc())
            .fetch()
            .groupBy({ it.value1()!! }, { it.value2()?.filterNotNull().orEmpty() })
            .mapValues { (_, runs) -> runs.first() }
    }
}
