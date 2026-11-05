package io.wid.dearby.application.discord

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.util.*

// 디스코드 연결(native-v1.md "디스코드 연결 구현 기록 — 2026-11-05"). 값이 하나라도 비면 디스코드 API는 503이고 GET /v1/me는 connections.discord.available=false.
// core에 두는 이유: 토큰 암호화(DiscordTokenCipher)와 설정 여부를 auth 모듈의 GET /v1/me도 읽는다. 어드민에는 값이 없어 항상 꺼져 있다
@ConfigurationProperties("dearby.discord")
data class DiscordProperties(
    val clientId: String? = null, // 디스코드 개발자 포털 앱의 Application ID
    val clientSecret: String? = null, // 같은 앱의 OAuth2 Client Secret. 저장소·로그·채팅에 넣지 않는다
    val botToken: String? = null, // 같은 앱의 Bot Token. 저장소·로그·채팅에 넣지 않는다
    val tokenKey: String? = null, // 사용자 토큰 암호화 키: 32바이트를 base64로(openssl rand -base64 32). 바꾸면 저장된 토큰을 읽지 못해 다시 연결해야 한다
    val callbackUrl: String? = null, // 계정 연결 콜백(GET /v1/me/connections/discord/callback). 포털 OAuth2 Redirects에 같은 값을 등록
    val botCallbackUrl: String? = null, // 봇 서버 연결 콜백(GET /v1/me/discord/guilds/callback). 포털 OAuth2 Redirects에 같은 값을 등록
    val appRedirectUri: String = "dearby://connections/discord", // iOS·Android: 콜백이 결과 code 또는 error를 붙여 돌려보내는 주소
    val webRedirectUri: String = "https://dearby.wid.io.kr/settings", // 웹: 같은 결과를 붙여 돌려보내는 주소
) {
    fun isConfigured(): Boolean =
        listOf(clientId, clientSecret, botToken, callbackUrl, botCallbackUrl).none { it.isNullOrBlank() } && tokenKeyBytes() != null

    // 형식이 틀린 키는 없는 것과 같이 본다(기동은 막지 않고 디스코드만 503)
    fun tokenKeyBytes(): ByteArray? =
        tokenKey?.trim()?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }?.takeIf { it.size == 32 }
}

@Configuration
@EnableConfigurationProperties(DiscordProperties::class)
class DiscordConfig
