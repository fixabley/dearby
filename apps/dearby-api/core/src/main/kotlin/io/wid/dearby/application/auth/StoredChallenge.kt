package io.wid.dearby.application.auth

import io.wid.dearby.domain.UserId

// 가입 challenge는 새 계정 정보를 함께 가진다. 로그인 challenge는 challenge만 가진다
data class StoredChallenge(
    val challenge: ByteArray,
    val userId: UserId?,
    val userEntityId: String?,
    val displayName: String?,
)
