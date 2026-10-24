package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITY_TAGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PROFILE_ELIGIBILITY
import io.wid.dearby.adaptor.persistence.jooq.tables.references.RECOMMENDATION_PREFERENCES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.TAGS
import io.wid.dearby.application.recommendation.RecommendationRepository
import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.TagKind
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.recommendation.EligibilityProfile
import io.wid.dearby.domain.recommendation.RecommendationPreferences
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Repository
class JooqRecommendationRepository(private val dsl: DSLContext) : RecommendationRepository {

    override fun findPreferences(userId: UserId): RecommendationPreferences? =
        dsl.selectFrom(RECOMMENDATION_PREFERENCES).where(RECOMMENDATION_PREFERENCES.USER_ID.eq(userId)).fetchOne()?.let { row ->
            RecommendationPreferences(
                fieldTagIds = row.fieldTagIds.orEmpty().filterNotNull(),
                // 종류 코드가 줄면 모르는 값은 버린다
                categories = row.categories.orEmpty().mapNotNull { code -> ActivityCategory.entries.firstOrNull { it.name == code } },
                traitTagIds = row.traitTagIds.orEmpty().filterNotNull(),
                completedAt = row.completedAt,
            )
        }

    override fun savePreferences(userId: UserId, preferences: RecommendationPreferences) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val fieldIds = preferences.fieldTagIds.toTypedArray<Long?>()
        val categories = preferences.categories.map { it.name }.toTypedArray<String?>()
        val traitIds = preferences.traitTagIds.toTypedArray<Long?>()
        dsl.insertInto(RECOMMENDATION_PREFERENCES)
            .set(RECOMMENDATION_PREFERENCES.USER_ID, userId)
            .set(RECOMMENDATION_PREFERENCES.FIELD_TAG_IDS, fieldIds)
            .set(RECOMMENDATION_PREFERENCES.CATEGORIES, categories)
            .set(RECOMMENDATION_PREFERENCES.TRAIT_TAG_IDS, traitIds)
            .set(RECOMMENDATION_PREFERENCES.COMPLETED_AT, now)
            .set(RECOMMENDATION_PREFERENCES.UPDATED_AT, now)
            .onConflict(RECOMMENDATION_PREFERENCES.USER_ID).doUpdate()
            .set(RECOMMENDATION_PREFERENCES.FIELD_TAG_IDS, fieldIds)
            .set(RECOMMENDATION_PREFERENCES.CATEGORIES, categories)
            .set(RECOMMENDATION_PREFERENCES.TRAIT_TAG_IDS, traitIds)
            .set(RECOMMENDATION_PREFERENCES.COMPLETED_AT, DSL.coalesce(RECOMMENDATION_PREFERENCES.COMPLETED_AT, DSL.`val`(now)))
            .set(RECOMMENDATION_PREFERENCES.UPDATED_AT, now)
            .execute()
    }

    override fun findEligibility(userId: UserId): EligibilityProfile? =
        dsl.selectFrom(PROFILE_ELIGIBILITY).where(PROFILE_ELIGIBILITY.USER_ID.eq(userId)).fetchOne()?.let {
            EligibilityProfile(it.educationStatus, it.employment, it.schoolYear?.toInt(), it.birthYear?.toInt(), it.consentVersion!!, it.consentedAt!!)
        }

    override fun saveEligibility(userId: UserId, profile: EligibilityProfile) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val record = dsl.newRecord(PROFILE_ELIGIBILITY).apply {
            this.userId = userId
            educationStatus = profile.educationStatus
            employment = profile.employment
            schoolYear = profile.schoolYear?.toShort()
            birthYear = profile.birthYear?.toShort()
            consentVersion = profile.consentVersion
            consentedAt = profile.consentedAt
            updatedAt = now
        }
        dsl.insertInto(PROFILE_ELIGIBILITY).set(record).onConflict(PROFILE_ELIGIBILITY.USER_ID).doUpdate().set(record).execute()
    }

    override fun deleteEligibility(userId: UserId) {
        dsl.deleteFrom(PROFILE_ELIGIBILITY).where(PROFILE_ELIGIBILITY.USER_ID.eq(userId)).execute()
    }

    override fun fieldRelevance(activityIds: Collection<Id>): Map<Id, Map<Id, Int?>> {
        if (activityIds.isEmpty()) return emptyMap()
        return dsl.select(ACTIVITY_TAGS.ACTIVITY_ID, ACTIVITY_TAGS.TAG_ID, ACTIVITY_TAGS.RELEVANCE)
            .from(ACTIVITY_TAGS).join(TAGS).on(TAGS.ID.eq(ACTIVITY_TAGS.TAG_ID))
            .where(ACTIVITY_TAGS.ACTIVITY_ID.`in`(activityIds), TAGS.KIND.eq(TagKind.FIELD.name))
            .fetch()
            .groupBy { it.value1()!! }
            .mapValues { (_, rows) -> rows.associate { it.value2()!! to it.value3()?.toInt() } }
    }
}
