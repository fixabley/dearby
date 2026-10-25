package io.wid.dearby.application.push

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.push.PushDevice
import io.wid.dearby.domain.push.PushPlatform
import java.time.OffsetDateTime

// 저장소 포트(기기 토큰·알림 설정). 보낸 기록은 NotificationRepository. 구현은 adaptor/persistence. users cascade
interface PushRepository {

    // 토큰은 한 사용자에게만: 다른 사용자가 같은 토큰을 올리면 옮겨 간다
    fun saveDevice(userId: UserId, platform: PushPlatform, token: String, timeZone: String, appVersion: String?, at: OffsetDateTime)
    fun deleteDevice(userId: UserId, token: String)
    fun deleteToken(token: String) // APNs 410·FCM UNREGISTERED
    fun devices(): List<PushDevice> // 사용자마다 최근에 올린 기기부터

    fun newRecruitmentEnabled(userId: UserId): Boolean // 행이 없으면 켜짐
    fun saveNewRecruitment(userId: UserId, enabled: Boolean, at: OffsetDateTime)
    fun disabledUsers(userIds: Collection<UserId>): Set<UserId>
}
