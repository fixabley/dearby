package io.wid.dearby.application.guest

import java.time.OffsetDateTime

// 홈 화면 세션 잇기 행. tokenCiphertext는 쓰이거나 만료되면 지운다
data class GuestHandoff(val digest: String, val sessionDigest: String, val tokenCiphertext: String?, val expiresAt: OffsetDateTime, val usedAt: OffsetDateTime?)
