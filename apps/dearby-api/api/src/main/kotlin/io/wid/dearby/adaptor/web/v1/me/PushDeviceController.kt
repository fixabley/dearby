package io.wid.dearby.adaptor.web.v1.me

import io.wid.dearby.application.push.PushDeviceService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

// native-v1.md "푸시 알림": 기기 토큰(로그아웃·알림 끄기에 지운다)과 알림 전체 스위치. 토큰은 로그에 남기지 않는다
@RestController
@RequestMapping("/v1/me")
class PushDeviceController(private val push: PushDeviceService) {

    @PutMapping("/push-devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun register(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: PushDeviceRequest) =
        push.register(UUID.fromString(jwt.subject), request.platform, request.token, request.timeZone, request.appVersion)

    @DeleteMapping("/push-devices/{token}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unregister(@AuthenticationPrincipal jwt: Jwt, @PathVariable token: String) = push.unregister(UUID.fromString(jwt.subject), token)

    @GetMapping("/notification-settings")
    fun settings(@AuthenticationPrincipal jwt: Jwt) = NotificationSettingsResponse(push.newRecruitmentEnabled(UUID.fromString(jwt.subject)))

    @PutMapping("/notification-settings")
    fun saveSettings(@AuthenticationPrincipal jwt: Jwt, @RequestBody request: NotificationSettingsResponse): NotificationSettingsResponse {
        push.setNewRecruitment(UUID.fromString(jwt.subject), request.newRecruitment)
        return request
    }
}

// timeZone: IANA 이름(없으면 Asia/Seoul). 그 시간대 아침 8시에 보낸다
data class PushDeviceRequest(val platform: String, val token: String, val timeZone: String? = null, val appVersion: String? = null)

// newRecruitment: 알림 전체 스위치(기본 켜짐)
data class NotificationSettingsResponse(val newRecruitment: Boolean)
