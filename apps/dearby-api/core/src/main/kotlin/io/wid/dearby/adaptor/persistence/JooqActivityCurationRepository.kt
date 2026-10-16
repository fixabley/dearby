package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_ROLES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.DISMISSED_ACTIVITY_SOURCES
import io.wid.dearby.application.activity.ActivityCurationRepository
import io.wid.dearby.domain.*
import io.wid.dearby.domain.curation.ActivityCuration
import io.wid.dearby.domain.curation.Freshness
import io.wid.dearby.domain.curation.RecruitmentStatus
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqActivityCurationRepository(private val dsl: DSLContext) : ActivityCurationRepository {

    private val json = JsonMapper.builder().findAndAddModules().build()

    override fun findAllByActivityId(activityIds: Collection<Id>): List<ActivityCuration> {
        if (activityIds.isEmpty()) return emptyList()
        val roles = dsl.selectFrom(ACTIVITY_ROLES)
            .where(ACTIVITY_ROLES.ACTIVITY_ID.`in`(activityIds))
            .orderBy(ACTIVITY_ROLES.POSITION)
            .fetch()
            .groupBy({ it.activityId!! }, { it.name!! })
        return dsl.selectFrom(ACTIVITIES).where(ACTIVITIES.ID.`in`(activityIds)).fetch().map {
            ActivityCuration(
                activityId = it.id!!,
                publicationStatus = PublicationStatus.valueOf(it.publicationStatus!!),
                officialSource = it.officialSource!!,
                participationType = ParticipationType.valueOf(it.participationType!!),
                recruitmentStatus = RecruitmentStatus.valueOf(it.recruitmentStatus!!),
                location = it.location,
                cost = it.cost,
                audience = it.audience,
                qualification = it.qualification?.data()?.let { data -> json.readValue<Map<String, List<String>>>(data) }.orEmpty(),
                roles = roles[it.id].orEmpty(),
                applicationUrl = it.applicationUrl,
                sourceCheckedAt = it.sourceCheckedAt,
                validUntil = it.validUntil,
                freshness = Freshness.valueOf(it.freshness!!),
                sourceNote = it.sourceNote!!,
            )
        }
    }

    @Transactional
    override fun delete(activityId: Id): Boolean {
        val sourceId = dsl.select(ACTIVITIES.SOURCE_ID).from(ACTIVITIES).where(ACTIVITIES.ID.eq(activityId)).fetchOne()
            ?: return false
        sourceId.value1()?.let {
            dsl.insertInto(DISMISSED_ACTIVITY_SOURCES)
                .set(DISMISSED_ACTIVITY_SOURCES.SOURCE_ID, it)
                .set(DISMISSED_ACTIVITY_SOURCES.DISMISSED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .onConflictDoNothing()
                .execute()
        }
        return dsl.deleteFrom(ACTIVITIES).where(ACTIVITIES.ID.eq(activityId)).execute() > 0
    }

    override fun updateTexts(activityId: Id, name: String, nameEn: String?, description: String, descriptionEn: String?): Boolean =
        dsl.update(ACTIVITIES)
            .set(ACTIVITIES.NAME, name)
            .set(ACTIVITIES.NAME_EN, nameEn)
            .set(ACTIVITIES.DESCRIPTION, description)
            .set(ACTIVITIES.DESCRIPTION_EN, descriptionEn)
            .set(ACTIVITIES.UPDATED_AT, LocalDateTime.now())
            .where(ACTIVITIES.ID.eq(activityId))
            .execute() > 0

    override fun updatePublicationStatus(activityId: Id, status: PublicationStatus): Boolean =
        dsl.update(ACTIVITIES)
            .set(ACTIVITIES.PUBLICATION_STATUS, status.name)
            .set(ACTIVITIES.UPDATED_AT, LocalDateTime.now())
            .where(ACTIVITIES.ID.eq(activityId))
            .execute() > 0
}
