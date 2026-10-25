package io.wid.dearby.application.push

import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.push.PushPlatform
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DateTimeException
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

// 푸시 기기 토큰과 알림 전체 스위치(native-v1.md "푸시 알림"). 로그인 사용자만. 토큰은 로그에 남기지 않는다
@Service
class PushDeviceService(private val push: PushRepository) {

    // 같은 토큰이면 시간대·앱 판만 새로 한다. 시간대가 없으면 서울
    @Transactional
    fun register(userId: UserId, platform: String, token: String, timeZone: String?, appVersion: String?) {
        val parsed = PushPlatform.entries.firstOrNull { it.name == platform } ?: throw InvalidInputException("platform은 IOS 또는 ANDROID입니다")
        if (token.isBlank() || token.length > MAX_TOKEN) throw InvalidInputException("token이 올바르지 않습니다")
        val zone = timeZone?.takeIf { it.isNotBlank() } ?: DEFAULT_ZONE
        try {
            ZoneId.of(zone)
        } catch (_: DateTimeException) {
            throw InvalidInputException("timeZone은 IANA 시간대 이름입니다(예: Asia/Seoul)")
        }
        if (zone.length > MAX_ZONE || (appVersion?.length ?: 0) > MAX_VERSION) throw InvalidInputException("값이 너무 깁니다")
        push.saveDevice(userId, parsed, token, zone, appVersion, OffsetDateTime.now(ZoneOffset.UTC))
    }

    // 없어도 그대로(로그아웃·알림 끄기)
    @Transactional
    fun unregister(userId: UserId, token: String) = push.deleteDevice(userId, token)

    @Transactional(readOnly = true)
    fun newRecruitmentEnabled(userId: UserId): Boolean = push.newRecruitmentEnabled(userId)

    @Transactional
    fun setNewRecruitment(userId: UserId, enabled: Boolean) = push.saveNewRecruitment(userId, enabled, OffsetDateTime.now(ZoneOffset.UTC))

    private companion object {
        const val DEFAULT_ZONE = "Asia/Seoul"
        const val MAX_TOKEN = 500
        const val MAX_ZONE = 64
        const val MAX_VERSION = 50
    }
}
