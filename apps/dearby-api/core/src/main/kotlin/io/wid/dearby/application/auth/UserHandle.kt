package io.wid.dearby.application.auth

import io.wid.dearby.domain.UserId
import java.nio.ByteBuffer
import java.util.*

// 패스키 user handle(user_entities.id) = 사용자 UUID 16바이트의 base64url(2026-10-15부터). 그 전 가입분은 handle이 무작위라 user_entities.name에 UUID 문자열이 남아 있다
fun userHandle(userId: UserId): String =
    Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteBuffer.allocate(16).putLong(userId.mostSignificantBits).putLong(userId.leastSignificantBits).array())
