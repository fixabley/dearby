package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.records.JobsRecord
import io.wid.dearby.adaptor.persistence.jooq.tables.references.JOBS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROFILE_JOBS
import io.wid.dearby.application.profile.JobRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.profile.Job
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqJobRepository(private val dsl: DSLContext) : JobRepository {

    override fun findAllById(ids: Collection<Id>): List<Job> {
        if (ids.isEmpty()) return emptyList()
        return dsl.selectFrom(JOBS).where(JOBS.ID.`in`(ids)).fetch().map(::toJob)
    }

    override fun search(q: String?, parentId: Id?, rootsOnly: Boolean, after: Id?, size: Int): List<Job> {
        val conditions = buildList<Condition> {
            q?.takeIf { it.isNotBlank() }?.trim()?.let { add(JOBS.NAME.containsIgnoreCase(it).or(JOBS.NAME_EN.containsIgnoreCase(it))) }
            parentId?.let { add(JOBS.PARENT_ID.eq(it)) }
            if (rootsOnly) add(JOBS.PARENT_ID.isNull)
            after?.let { add(JOBS.ID.gt(it)) }
        }
        return dsl.selectFrom(JOBS).where(conditions).orderBy(JOBS.ID).limit(size).fetch().map(::toJob)
    }

    override fun create(name: String, nameEn: String?, parentId: Id?): Id {
        val now = now()
        return dsl.insertInto(JOBS)
            .set(JOBS.NAME, name).set(JOBS.NAME_EN, nameEn).set(JOBS.PARENT_ID, parentId)
            .set(JOBS.CREATED_AT, now).set(JOBS.UPDATED_AT, now)
            .returning(JOBS.ID).fetchOne()!!.id!!
    }

    override fun update(id: Id, name: String, nameEn: String?, parentId: Id?) {
        dsl.update(JOBS).set(JOBS.NAME, name).set(JOBS.NAME_EN, nameEn).set(JOBS.PARENT_ID, parentId).set(JOBS.UPDATED_AT, now())
            .where(JOBS.ID.eq(id)).execute()
    }

    override fun existsSiblingName(parentId: Id?, name: String, exceptId: Id?, english: Boolean): Boolean =
        dsl.fetchExists(
            JOBS,
            parentId?.let { JOBS.PARENT_ID.eq(it) } ?: JOBS.PARENT_ID.isNull,
            withoutSpaces(if (english) JOBS.NAME_EN else JOBS.NAME).eq(withoutSpaces(DSL.`val`(name))),
            exceptId?.let { JOBS.ID.ne(it) } ?: DSL.noCondition(),
        )

    override fun delete(id: Id): Boolean = dsl.deleteFrom(JOBS).where(JOBS.ID.eq(id)).execute() > 0

    override fun ancestorIds(id: Id): List<Id> = walkUp(id, parents())

    override fun ancestors(ids: Collection<Id>): Map<Id, List<Pair<Id, String>>> {
        if (ids.isEmpty()) return emptyMap()
        val rows = dsl.select(JOBS.ID, JOBS.PARENT_ID, JOBS.NAME).from(JOBS).fetch()
        val parents = rows.associate { it.value1()!! to it.value2() }
        val names = rows.associate { it.value1()!! to it.value3()!! }
        return ids.associateWith { id -> walkUp(id, parents).reversed().map { it to names.getValue(it) } }
    }

    override fun childCounts(ids: Collection<Id>): Map<Id, Int> {
        if (ids.isEmpty()) return emptyMap()
        return dsl.select(JOBS.PARENT_ID, DSL.count()).from(JOBS).where(JOBS.PARENT_ID.`in`(ids)).groupBy(JOBS.PARENT_ID)
            .fetch().associate { it.value1()!! to it.value2()!! }
    }

    override fun inUse(id: Id): Boolean = dsl.fetchExists(PROFILE_JOBS, PROFILE_JOBS.JOB_ID.eq(id))

    // 직무 수가 적어 전체 (id, 상위 id)를 한 번 읽어 따라 올라간다. 데이터가 이미 순환이어도 멈춘다
    private fun parents(): Map<Id, Id?> = dsl.select(JOBS.ID, JOBS.PARENT_ID).from(JOBS).fetch().associate { it.value1()!! to it.value2() }

    private fun walkUp(id: Id, parents: Map<Id, Id?>): List<Id> {
        val result = mutableListOf<Id>()
        var current = parents[id]
        while (current != null && current != id && current !in result) {
            result += current
            current = parents[current]
        }
        return result
    }

    private fun toJob(it: JobsRecord) = Job(it.id!!, it.name!!, it.nameEn, it.parentId, it.createdAt!!, it.updatedAt!!)

    private fun now() = OffsetDateTime.now(ZoneOffset.UTC)
}
