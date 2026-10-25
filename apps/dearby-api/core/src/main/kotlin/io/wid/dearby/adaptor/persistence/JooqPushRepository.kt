package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.NOTIFICATION_SETTINGS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.PUSH_DEVICES
import io.wid.dearby.application.push.PushRepository
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.push.PushDevice
import io.wid.dearby.domain.push.PushPlatform
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class JooqPushRepository(private val dsl: DSLContext) : PushRepository {

    override fun saveDevice(userId: UserId, platform: PushPlatform, token: String, timeZone: String, appVersion: String?, at: OffsetDateTime) {
        dsl.insertInto(PUSH_DEVICES)
            .set(PUSH_DEVICES.TOKEN, token)
            .set(PUSH_DEVICES.USER_ID, userId)
            .set(PUSH_DEVICES.PLATFORM, platform.name)
            .set(PUSH_DEVICES.TIME_ZONE, timeZone)
            .set(PUSH_DEVICES.APP_VERSION, appVersion)
            .set(PUSH_DEVICES.UPDATED_AT, at)
            .onConflict(PUSH_DEVICES.TOKEN).doUpdate()
            .set(PUSH_DEVICES.USER_ID, userId)
            .set(PUSH_DEVICES.PLATFORM, platform.name)
            .set(PUSH_DEVICES.TIME_ZONE, timeZone)
            .set(PUSH_DEVICES.APP_VERSION, appVersion)
            .set(PUSH_DEVICES.UPDATED_AT, at)
            .execute()
    }

    override fun deleteDevice(userId: UserId, token: String) {
        dsl.deleteFrom(PUSH_DEVICES).where(PUSH_DEVICES.USER_ID.eq(userId), PUSH_DEVICES.TOKEN.eq(token)).execute()
    }

    override fun deleteToken(token: String) {
        dsl.deleteFrom(PUSH_DEVICES).where(PUSH_DEVICES.TOKEN.eq(token)).execute()
    }

    override fun devices(): List<PushDevice> =
        dsl.selectFrom(PUSH_DEVICES).orderBy(PUSH_DEVICES.USER_ID, PUSH_DEVICES.UPDATED_AT.desc(), PUSH_DEVICES.TOKEN)
            .fetch { PushDevice(it.token!!, it.userId!!, PushPlatform.valueOf(it.platform!!), it.timeZone!!) }

    override fun newRecruitmentEnabled(userId: UserId): Boolean =
        dsl.select(NOTIFICATION_SETTINGS.NEW_RECRUITMENT).from(NOTIFICATION_SETTINGS).where(NOTIFICATION_SETTINGS.USER_ID.eq(userId)).fetchOne()?.value1() ?: true

    override fun saveNewRecruitment(userId: UserId, enabled: Boolean, at: OffsetDateTime) {
        dsl.insertInto(NOTIFICATION_SETTINGS)
            .set(NOTIFICATION_SETTINGS.USER_ID, userId)
            .set(NOTIFICATION_SETTINGS.NEW_RECRUITMENT, enabled)
            .set(NOTIFICATION_SETTINGS.UPDATED_AT, at)
            .onConflict(NOTIFICATION_SETTINGS.USER_ID).doUpdate()
            .set(NOTIFICATION_SETTINGS.NEW_RECRUITMENT, enabled)
            .set(NOTIFICATION_SETTINGS.UPDATED_AT, at)
            .execute()
    }

    override fun disabledUsers(userIds: Collection<UserId>): Set<UserId> {
        if (userIds.isEmpty()) return emptySet()
        return dsl.select(NOTIFICATION_SETTINGS.USER_ID).from(NOTIFICATION_SETTINGS)
            .where(NOTIFICATION_SETTINGS.USER_ID.`in`(userIds), NOTIFICATION_SETTINGS.NEW_RECRUITMENT.isFalse)
            .fetchSet { it.value1()!! }
    }
}
