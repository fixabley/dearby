package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.ACTIVITIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PUSH_DELIVERIES
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_NOTIFICATIONS
import io.wid.dearby.application.notification.NotificationRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.notification.NotificationDraft
import io.wid.dearby.domain.notification.NotificationKind
import io.wid.dearby.domain.notification.UserNotification
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.OffsetDateTime

@Repository
class JooqNotificationRepository(private val dsl: DSLContext) : NotificationRepository {

    override fun saveAll(userId: UserId, drafts: List<NotificationDraft>, at: OffsetDateTime) {
        if (drafts.isEmpty()) return
        val hasNew = dsl.select(USER_NOTIFICATIONS.ACTIVITY_ID).from(USER_NOTIFICATIONS)
            .where(USER_NOTIFICATIONS.USER_ID.eq(userId), USER_NOTIFICATIONS.KIND.eq(NotificationKind.NEW.name), USER_NOTIFICATIONS.ACTIVITY_ID.`in`(drafts.map { it.activityId }))
            .fetchSet { it.value1()!! }
        drafts.filterNot { it.kind == NotificationKind.NEW && it.activityId in hasNew }.forEach { d ->
            dsl.insertInto(USER_NOTIFICATIONS)
                .set(USER_NOTIFICATIONS.USER_ID, userId)
                .set(USER_NOTIFICATIONS.ACTIVITY_ID, d.activityId)
                .set(USER_NOTIFICATIONS.PROGRAM_ID, d.programId)
                .set(USER_NOTIFICATIONS.KIND, d.kind.name)
                .set(USER_NOTIFICATIONS.LOCAL_DATE, d.localDate)
                .set(USER_NOTIFICATIONS.CREATED_AT, at)
                .onConflictDoNothing()
                .execute()
        }
    }

    override fun forPush(userId: UserId, date: LocalDate, newSince: OffsetDateTime): List<UserNotification> =
        select(
            USER_NOTIFICATIONS.USER_ID.eq(userId).and(
                USER_NOTIFICATIONS.LOCAL_DATE.eq(date).or(USER_NOTIFICATIONS.KIND.eq(NotificationKind.NEW.name).and(USER_NOTIFICATIONS.CREATED_AT.gt(newSince)))
            )
        )

    override fun delivered(notificationIds: Collection<Id>, deviceToken: String): Set<Id> {
        if (notificationIds.isEmpty()) return emptySet()
        return dsl.select(PUSH_DELIVERIES.NOTIFICATION_ID).from(PUSH_DELIVERIES)
            .where(PUSH_DELIVERIES.DEVICE_TOKEN.eq(deviceToken), PUSH_DELIVERIES.NOTIFICATION_ID.`in`(notificationIds))
            .fetchSet { it.value1()!! }
    }

    override fun saveDeliveries(notificationIds: Collection<Id>, deviceToken: String, at: OffsetDateTime) {
        notificationIds.forEach { id ->
            dsl.insertInto(PUSH_DELIVERIES)
                .set(PUSH_DELIVERIES.NOTIFICATION_ID, id)
                .set(PUSH_DELIVERIES.DEVICE_TOKEN, deviceToken)
                .set(PUSH_DELIVERIES.SENT_AT, at)
                .onConflictDoNothing()
                .execute()
        }
    }

    override fun programPage(userId: UserId, cursor: Int, size: Int): List<Id> {
        val latest = DSL.max(USER_NOTIFICATIONS.CREATED_AT)
        return dsl.select(USER_NOTIFICATIONS.PROGRAM_ID, latest).from(USER_NOTIFICATIONS)
            .where(USER_NOTIFICATIONS.USER_ID.eq(userId))
            .groupBy(USER_NOTIFICATIONS.PROGRAM_ID)
            .orderBy(latest.desc(), USER_NOTIFICATIONS.PROGRAM_ID.desc())
            .limit(size).offset(cursor)
            .fetch { it.value1()!! }
    }

    override fun findByPrograms(userId: UserId, programIds: Collection<Id>): List<UserNotification> {
        if (programIds.isEmpty()) return emptyList()
        return select(USER_NOTIFICATIONS.USER_ID.eq(userId).and(USER_NOTIFICATIONS.PROGRAM_ID.`in`(programIds)))
    }

    override fun unreadCount(userId: UserId): Int =
        dsl.fetchCount(USER_NOTIFICATIONS, USER_NOTIFICATIONS.USER_ID.eq(userId), USER_NOTIFICATIONS.READ_AT.isNull)

    override fun markRead(userId: UserId, id: Id, at: OffsetDateTime): Boolean =
        dsl.update(USER_NOTIFICATIONS)
            .set(USER_NOTIFICATIONS.READ_AT, DSL.coalesce(USER_NOTIFICATIONS.READ_AT, DSL.`val`(at)))
            .where(USER_NOTIFICATIONS.USER_ID.eq(userId), USER_NOTIFICATIONS.ID.eq(id))
            .execute() > 0

    override fun markAllRead(userId: UserId, at: OffsetDateTime) {
        dsl.update(USER_NOTIFICATIONS).set(USER_NOTIFICATIONS.READ_AT, at)
            .where(USER_NOTIFICATIONS.USER_ID.eq(userId), USER_NOTIFICATIONS.READ_AT.isNull)
            .execute()
    }

    override fun deleteBefore(at: OffsetDateTime): Int =
        dsl.deleteFrom(USER_NOTIFICATIONS).where(USER_NOTIFICATIONS.CREATED_AT.lt(at)).execute()

    // 최근 순(같은 시각이면 id 역순)
    private fun select(condition: Condition): List<UserNotification> =
        dsl.select(USER_NOTIFICATIONS.asterisk(), ACTIVITIES.NAME)
            .from(USER_NOTIFICATIONS).join(ACTIVITIES).on(ACTIVITIES.ID.eq(USER_NOTIFICATIONS.ACTIVITY_ID))
            .where(condition)
            .orderBy(USER_NOTIFICATIONS.CREATED_AT.desc(), USER_NOTIFICATIONS.ID.desc())
            .fetch(::toNotification)

    private fun toNotification(row: Record) = UserNotification(
        id = row.get(USER_NOTIFICATIONS.ID)!!,
        userId = row.get(USER_NOTIFICATIONS.USER_ID)!!,
        activityId = row.get(USER_NOTIFICATIONS.ACTIVITY_ID)!!,
        activityName = row.get(ACTIVITIES.NAME)!!,
        programId = row.get(USER_NOTIFICATIONS.PROGRAM_ID)!!,
        kind = NotificationKind.valueOf(row.get(USER_NOTIFICATIONS.KIND)!!),
        localDate = row.get(USER_NOTIFICATIONS.LOCAL_DATE)!!,
        createdAt = row.get(USER_NOTIFICATIONS.CREATED_AT)!!,
        readAt = row.get(USER_NOTIFICATIONS.READ_AT),
    )
}
