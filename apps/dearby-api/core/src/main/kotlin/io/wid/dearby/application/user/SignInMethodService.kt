package io.wid.dearby.application.user

import io.wid.dearby.application.auth.AppleSignInService
import io.wid.dearby.application.auth.AppleTokenClient
import io.wid.dearby.application.auth.GoogleSignInService
import io.wid.dearby.application.auth.PasskeyCredentialRepository
import io.wid.dearby.application.auth.StoredPasskey
import io.wid.dearby.domain.ConflictException
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.UserId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

// 설정 화면의 로그인 수단(native-v1.md "설정 — 2026-10-19 밤"): 패스키 목록·이름·삭제, 연결된 애플·구글 계정 목록·해제.
// 마지막 수단(패스키 수 + 연결된 외부 계정 수가 1)은 지울 수 없다(409 LAST_SIGN_IN_METHOD). 패스키 추가는 WebAuthn 가입 흐름이 맡는다
@Service
class SignInMethodService(
    private val users: UserRepository,
    private val identities: UserIdentityRepository,
    private val passkeys: PasskeyCredentialRepository,
    private val appleTokens: AppleTokenClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun summary(userId: UserId): SignInMethodSummary {
        val createdAt = users.findCreatedAt(userId) ?: throw NotFoundException("계정을 찾을 수 없습니다", "User not found: $userId")
        val linked = identities.findAllByUser(userId).map { it.provider }
        return SignInMethodSummary(
            createdAt = createdAt,
            passkeys = passkeys.findAllByUser(userId).size,
            apple = AppleSignInService.PROVIDER in linked,
            google = GoogleSignInService.PROVIDER in linked,
        )
    }

    fun identities(userId: UserId): List<StoredIdentity> = identities.findAllByUser(userId).sortedBy { it.provider }

    // provider는 경로의 소문자 이름(apple·google). 애플은 저장한 refresh token을 먼저 폐기한다(계정 삭제와 같은 경로)
    @Transactional
    fun unlink(userId: UserId, provider: String) {
        val stored = PROVIDERS[provider] ?: throw NotFoundException("모르는 로그인 수단입니다", "Unknown provider: $provider")
        val linked = identities.findAllByUser(userId)
        val identity = linked.firstOrNull { it.provider == stored }
            ?: throw NotFoundException("연결된 $provider 계정이 없습니다", "No $stored identity for user $userId")
        requireAnotherMethod(userId, passkeys.findAllByUser(userId).size + linked.size)
        if (stored == AppleSignInService.PROVIDER) revokeAppleToken(userId, identity)
        identities.delete(stored, userId)
    }

    fun passkeys(userId: UserId): List<StoredPasskey> = passkeys.findAllByUser(userId)

    // label은 앞뒤 공백을 지운 1~60자
    fun renamePasskey(userId: UserId, credentialId: String, label: String): StoredPasskey {
        val trimmed = label.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LABEL) throw InvalidInputException("label은 1~${MAX_LABEL}자여야 합니다")
        return passkeys.rename(userId, credentialId, trimmed) ?: throw notFoundPasskey(userId, credentialId)
    }

    @Transactional
    fun deletePasskey(userId: UserId, credentialId: String) {
        val mine = passkeys.findAllByUser(userId)
        if (mine.none { it.credentialId == credentialId }) throw notFoundPasskey(userId, credentialId)
        requireAnotherMethod(userId, mine.size + identities.findAllByUser(userId).size)
        if (!passkeys.delete(userId, credentialId)) throw notFoundPasskey(userId, credentialId)
    }

    // 계정 삭제·연결 해제 때 애플 토큰을 폐기한다. 토큰이 없거나 폐기가 실패해도 막지 않고 로그에 남긴다
    fun revokeAppleTokens(userId: UserId) =
        identities.findAllByUser(userId).filter { it.provider == AppleSignInService.PROVIDER }.forEach { revokeAppleToken(userId, it) }

    private fun revokeAppleToken(userId: UserId, identity: StoredIdentity) {
        val revoked = identity.refreshToken != null && identity.clientId != null && appleTokens.revoke(identity.refreshToken, identity.clientId)
        if (!revoked) log.warn("Apple token not revoked for user {} (token {})", userId, if (identity.refreshToken == null) "missing" else "rejected")
    }

    // methods = 패스키 수 + 연결된 외부 계정 수
    private fun requireAnotherMethod(userId: UserId, methods: Int) {
        if (methods <= 1) {
            throw ConflictException("마지막 로그인 수단은 지울 수 없습니다", "Last sign-in method for user $userId", code = LAST_SIGN_IN_METHOD)
        }
    }

    // 남의 패스키와 없는 패스키를 구분하지 않는다
    private fun notFoundPasskey(userId: UserId, credentialId: String) =
        NotFoundException("패스키를 찾을 수 없습니다", "Passkey $credentialId not owned by $userId")

    companion object {
        const val LAST_SIGN_IN_METHOD = "LAST_SIGN_IN_METHOD"
        const val MAX_LABEL = 60
        // 경로 이름 → user_identities.provider
        val PROVIDERS = mapOf("apple" to AppleSignInService.PROVIDER, "google" to GoogleSignInService.PROVIDER)
    }
}

data class SignInMethodSummary(val createdAt: OffsetDateTime, val passkeys: Int, val apple: Boolean, val google: Boolean)
