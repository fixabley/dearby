package io.wid.dearby.auth.web

import java.time.OffsetDateTime
import java.util.*

// GET /v1/me(native-v1.md "설정")
data class MeResponse(val id: UUID, val createdAt: OffsetDateTime, val signInMethods: SignInMethodsResponse, val connections: ConnectionsResponse)

data class SignInMethodsResponse(val passkeys: Int, val apple: Boolean, val google: Boolean)

// 로그인 수단이 아닌 연결(native-v1.md "디스코드 연결 구현 기록 — 2026-11-05"). signInMethods·마지막 로그인 수단 판정에 세지 않는다
data class ConnectionsResponse(val discord: DiscordConnectionResponse)

// available=false면 서버에 디스코드 설정이 없어 앱·웹은 행을 숨긴다. 연결 안 됨이면 username·linkedAt이 null
data class DiscordConnectionResponse(val available: Boolean, val username: String?, val linkedAt: OffsetDateTime?)
