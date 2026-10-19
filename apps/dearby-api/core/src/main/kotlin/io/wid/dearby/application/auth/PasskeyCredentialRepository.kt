package io.wid.dearby.application.auth

import io.wid.dearby.domain.UserId
import java.time.OffsetDateTime

// 사용자의 패스키(user_credentials, Spring Security 스키마). 쓰기는 패스키 가입(WebAuthn)이 하고 여기서는 설정 화면의 목록·이름·삭제만 다룬다. 구현은 adaptor/persistence
interface PasskeyCredentialRepository {

    fun findAllByUser(userId: UserId): List<StoredPasskey>

    // 그 사용자의 패스키가 아니면 null
    fun rename(userId: UserId, credentialId: String, label: String): StoredPasskey?

    // 그 사용자의 패스키가 아니면 false
    fun delete(userId: UserId, credentialId: String): Boolean
}

// credentialId는 base64url. transports는 등록 때 기기가 알려 준 값(iOS는 비어 있다)
data class StoredPasskey(
    val credentialId: String,
    val label: String,
    val createdAt: OffsetDateTime,
    val lastUsedAt: OffsetDateTime,
    val backedUp: Boolean,
    val transports: List<String>,
)
