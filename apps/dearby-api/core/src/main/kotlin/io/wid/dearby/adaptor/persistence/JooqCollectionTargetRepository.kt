package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.COLLECTION_RUNS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ORGANIZATIONS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAMS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROGRAM_COLLECTION_SETTINGS
import io.wid.dearby.application.collection.CollectionTargetRepository
import io.wid.dearby.domain.CollectionOutcome
import io.wid.dearby.domain.CollectionTarget
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class JooqCollectionTargetRepository(private val dsl: DSLContext) : CollectionTargetRepository {

    override fun findDue(today: LocalDate): List<CollectionTarget> {
        val settings = PROGRAM_COLLECTION_SETTINGS
        val interval = DSL.coalesce(settings.INTERVAL_DAYS, DEFAULT_INTERVAL_DAYS)
        // 마지막 성공 수집일 + 주기 > 오늘이면 아직 주기 안이다(date - integer = date)
        val collectedWithinInterval = DSL.exists(
            DSL.selectOne().from(COLLECTION_RUNS).where(
                COLLECTION_RUNS.PROGRAM_ID.eq(PROGRAMS.ID),
                COLLECTION_RUNS.OUTCOME.eq(CollectionOutcome.SUCCEEDED.name),
                COLLECTION_RUNS.RUN_DATE.gt(DSL.field("{0} - {1}", LocalDate::class.java, DSL.`val`(today), interval)),
            )
        )
        return dsl.select(PROGRAMS.ID, PROGRAMS.NAME, ORGANIZATIONS.NAME, settings.OFFICIAL_URL, settings.ALLOWED_HOSTS, interval)
            .from(PROGRAMS)
            .leftJoin(ORGANIZATIONS).on(ORGANIZATIONS.ID.eq(PROGRAMS.ORGANIZATION_ID))
            .leftJoin(settings).on(settings.PROGRAM_ID.eq(PROGRAMS.ID))
            .where(DSL.coalesce(settings.ENABLED, true).isTrue, DSL.not(collectedWithinInterval))
            .orderBy(PROGRAMS.ID)
            .fetch {
                CollectionTarget(
                    programId = it.value1()!!,
                    programName = it.value2()!!,
                    organizationName = it.value3(),
                    officialUrl = it.value4(),
                    allowedHosts = it.value5()?.filterNotNull().orEmpty(),
                    intervalDays = it.value6()!!,
                )
            }
    }

    private companion object {
        const val DEFAULT_INTERVAL_DAYS = 1
    }
}
