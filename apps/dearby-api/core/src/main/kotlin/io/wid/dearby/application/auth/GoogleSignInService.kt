package io.wid.dearby.application.auth

import io.wid.dearby.application.user.UserIdentityRepository
import io.wid.dearby.application.user.UserRepository
import io.wid.dearby.domain.AuthenticationFailedException
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.ServiceUnavailableException
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.user.User
import io.wid.dearby.domain.user.UserRole
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

// 구글 로그인(native-v1.md "구글 로그인 — 2026-10-19 네이티브 SDK로 전환"). 앱의 구글 SDK가 받은 ID 토큰을 서버가 검증한다.
// 이미 연결된 구글 계정이면 바로 로그인하고, 처음 보면 linkToken을 주어 새 계정 또는 패스키 계정 연결을 고르게 한다. 이메일로 계정을 합치지 않는다.
// 중간 상태(nonce·linkToken)는 애플과 같은 표(provider = GOOGLE)에 둔다. refresh token은 받지 않으므로 계정 삭제 때 폐기할 구글 토큰도 없다
@Service
class GoogleSignInService(
    private val google: GoogleIdProvider,
    private val states: AppleSignInStateRepository,
    private val identities: UserIdentityRepository,
    private val users: UserRepository,
    private val tokens: TokenService,
    private val transaction: TransactionTemplate,
) {

    // 앱이 구글 SDK 요청에 그대로 넣을 nonce. 계약은 nonce만 돌려주므로 상태 id는 nonce에서 정해지는 UUID로 둔다(nonce는 256비트 난수라 추측할 수 없다)
    fun nonce(): String {
        requireConfigured()
        val nonce = random()
        states.issue(AppleSignInStep.NONCE, AppleSignInState(nonce = nonce, provider = PROVIDER), stateId(nonce))
        return nonce
    }

    fun signIn(idToken: String, nonce: String): GoogleSignInResult {
        requireConfigured()
        val issued = states.consume(stateId(nonce), AppleSignInStep.NONCE, PROVIDER)?.nonce
            ?.takeIf { MessageDigest.isEqual(it.toByteArray(), nonce.toByteArray()) }
            ?: throw AuthenticationFailedException("nonce가 없거나 만료되었습니다")
        return resolve(google.verify(idToken, issued))
    }

    // 새 계정을 만든다. 같은 구글 계정으로 동시에 가입하면 먼저 만든 계정으로 로그인한다
    fun register(linkToken: UUID): TokenPair {
        val identity = consumeLink(linkToken)
        val created = try {
            transaction.execute {
                val user = User(UUID.randomUUID(), listOf(UserRole.USER))
                users.create(user)
                identities.create(PROVIDER, identity.subject, user.id, identity.email, identity.displayName, null, null)
                user
            }!!
        } catch (_: DuplicateKeyException) {
            existingUser(identity) ?: throw AuthenticationFailedException("구글 계정을 연결하지 못했습니다")
        }
        return tokens.issue(created)
    }

    // 로그인한 사용자(패스키 계정)에 구글 계정을 연결한다. 이미 그 사용자에 연결돼 있으면 그대로 성공
    fun link(userId: UserId, linkToken: UUID) {
        val identity = consumeLink(linkToken)
        val owner = identities.findUserId(PROVIDER, identity.subject)
        if (owner == userId) return
        if (owner != null) throw InvalidInputException("이 구글 계정은 다른 Dearby 계정에 연결되어 있습니다")
        if (identities.existsForUser(PROVIDER, userId)) throw InvalidInputException("이 계정에는 이미 다른 구글 계정이 연결되어 있습니다")
        users.findById(userId) ?: throw AuthenticationFailedException("계정을 찾을 수 없습니다")
        try {
            identities.create(PROVIDER, identity.subject, userId, identity.email, identity.displayName, null, null)
        } catch (_: DuplicateKeyException) {
            if (identities.findUserId(PROVIDER, identity.subject) != userId) {
                throw InvalidInputException("이 구글 계정은 다른 Dearby 계정에 연결되어 있습니다")
            }
        }
    }

    private fun resolve(identity: AppleIdentity): GoogleSignInResult {
        val user = existingUser(identity)
            ?: return GoogleSignInResult.NeedsAccount(
                states.issue(AppleSignInStep.LINK, AppleSignInState(identity = identity, provider = PROVIDER)), identity.email, identity.displayName,
            )
        return GoogleSignInResult.SignedIn(tokens.issue(user))
    }

    private fun existingUser(identity: AppleIdentity): User? =
        identities.findUserId(PROVIDER, identity.subject)?.let(users::findById)

    private fun consumeLink(linkToken: UUID): AppleIdentity =
        states.consume(linkToken, AppleSignInStep.LINK, PROVIDER)?.identity
            ?: throw AuthenticationFailedException("구글 로그인이 만료되었습니다. 다시 시도해 주세요")

    private fun requireConfigured() {
        if (!google.isConfigured()) throw ServiceUnavailableException("구글 로그인을 지금 쓸 수 없습니다")
    }

    private fun random(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        const val PROVIDER = "GOOGLE"

        fun stateId(nonce: String): UUID = UUID.nameUUIDFromBytes("dearby/google/nonce/$nonce".toByteArray())
    }
}
