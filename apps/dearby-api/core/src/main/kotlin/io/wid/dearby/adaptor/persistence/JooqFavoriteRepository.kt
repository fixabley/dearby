package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_FAVORITES
import io.wid.dearby.application.favorite.FavoriteRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.favorite.Favorite
import io.wid.dearby.domain.favorite.FavoriteTarget
import io.wid.dearby.domain.favorite.NotifyLevel
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class JooqFavoriteRepository(private val dsl: DSLContext) : FavoriteRepository {

    override fun save(userId: UserId, target: FavoriteTarget, targetId: Id, level: NotifyLevel?, at: OffsetDateTime) {
        val insert = dsl.insertInto(USER_FAVORITES)
            .set(USER_FAVORITES.USER_ID, userId)
            .set(USER_FAVORITES.TARGET_TYPE, target.name)
            .set(USER_FAVORITES.TARGET_ID, targetId)
            .set(USER_FAVORITES.NOTIFY_LEVEL, (level ?: NotifyLevel.APPLICATION).name)
            .set(USER_FAVORITES.CREATED_AT, at)
            .onConflict(USER_FAVORITES.USER_ID, USER_FAVORITES.TARGET_TYPE, USER_FAVORITES.TARGET_ID)
        if (level == null) insert.doNothing().execute() else insert.doUpdate().set(USER_FAVORITES.NOTIFY_LEVEL, level.name).execute()
    }

    override fun delete(userId: UserId, target: FavoriteTarget, targetId: Id) {
        dsl.deleteFrom(USER_FAVORITES).where(key(userId, target, targetId)).execute()
    }

    override fun markSeen(userId: UserId, programId: Id, at: OffsetDateTime) {
        dsl.update(USER_FAVORITES).set(USER_FAVORITES.SEEN_AT, at).where(key(userId, FavoriteTarget.PROGRAM, programId)).execute()
    }

    override fun findAll(userId: UserId): List<Favorite> =
        dsl.selectFrom(USER_FAVORITES).where(USER_FAVORITES.USER_ID.eq(userId)).orderBy(USER_FAVORITES.CREATED_AT.desc()).fetch {
            Favorite(FavoriteTarget.valueOf(it.targetType!!), it.targetId!!, NotifyLevel.valueOf(it.notifyLevel!!), it.createdAt!!, it.seenAt)
        }

    override fun userIds(): List<UserId> = dsl.selectDistinct(USER_FAVORITES.USER_ID).from(USER_FAVORITES).fetch { it.value1()!! }

    // 즐겨찾기 화면의 새 모집 배지와 신규 알림이 같은 규칙을 쓴다(열린 활동 판정은 OpenActivityConditions)
    override fun newRecruitments(userIds: Collection<UserId>, now: OffsetDateTime): Map<UserId, Map<Id, List<Id>>> {
        if (userIds.isEmpty()) return emptyMap()
        val since = DSL.greatest(USER_FAVORITES.CREATED_AT, DSL.coalesce(USER_FAVORITES.SEEN_AT, USER_FAVORITES.CREATED_AT))
        return dsl.select(USER_FAVORITES.USER_ID, ACTIVITIES.PROGRAM_ID, ACTIVITIES.ID)
            .from(USER_FAVORITES)
            .join(ACTIVITIES).on(ACTIVITIES.PROGRAM_ID.eq(USER_FAVORITES.TARGET_ID))
            .leftJoin(OpenActivityConditions.app).on(OpenActivityConditions.appJoin)
            .leftJoin(OpenActivityConditions.act).on(OpenActivityConditions.actJoin)
            .where(
                USER_FAVORITES.USER_ID.`in`(userIds), USER_FAVORITES.TARGET_TYPE.eq(FavoriteTarget.PROGRAM.name),
                ACTIVITIES.PUBLISHED_AT.gt(since), OpenActivityConditions.open(now),
            )
            .orderBy(ACTIVITIES.ID)
            .fetch()
            .groupBy({ it.value1()!! }, { it.value2()!! to it.value3()!! })
            .mapValues { (_, pairs) -> pairs.groupBy({ it.first }, { it.second }) }
    }

    private fun key(userId: UserId, target: FavoriteTarget, targetId: Id) =
        USER_FAVORITES.USER_ID.eq(userId).and(USER_FAVORITES.TARGET_TYPE.eq(target.name)).and(USER_FAVORITES.TARGET_ID.eq(targetId))
}
