package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_ROLES
import io.wid.dearby.application.activity.ActivityCurationRepository
import io.wid.dearby.domain.*
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime

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

    override fun updatePublicationStatus(activityId: Id, status: PublicationStatus): Boolean =
        dsl.update(ACTIVITIES)
            .set(ACTIVITIES.PUBLICATION_STATUS, status.name)
            .set(ACTIVITIES.UPDATED_AT, LocalDateTime.now())
            .where(ACTIVITIES.ID.eq(activityId))
            .execute() > 0
}
