package io.wid.dearby.application.auth

import io.wid.dearby.application.user.UserIdentityRepository
import io.wid.dearby.application.user.UserRepository
import io.wid.dearby.domain.AuthenticationFailedException
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.ServiceUnavailableException
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.user.User
import io.wid.dearby.domain.user.UserRole
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

// 애플 로그인(native-v1.md "애플 로그인"). 이미 연결된 애플 계정이면 바로 로그인하고, 처음 보면 linkToken을 주어
// 사용자가 새 계정을 만들거나 패스키 계정에 연결하게 한다. 이메일로 계정을 합치지 않는다
@Service
class AppleSignInService(
    private val apple: AppleIdProvider,
    private val states: AppleSignInStateRepository,
    private val identities: UserIdentityRepository,
    private val appleTokens: AppleTokenClient,
    private val users: UserRepository,
    private val tokens: TokenService,
    private val transaction: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 앱(iOS)·웹이 애플 인가 요청에 그대로 넣을 nonce
    fun nonce(): Pair<UUID, String> {
        requireConfigured()
        val nonce = random()
        return states.issue(AppleSignInStep.NONCE, AppleSignInState(nonce = nonce)) to nonce
    }

    // authorizationCode는 iOS가 받은 값. 애플 토큰으로 바꿔 두면 계정 삭제 때 폐기할 수 있다
    fun signIn(challengeId: UUID, identityToken: String, displayName: String?, authorizationCode: String? = null): AppleSignInResult {
        requireConfigured()
        val nonce = states.consume(challengeId, AppleSignInStep.NONCE)?.takeIf { it.verifierHash == null }?.nonce
            ?: throw AuthenticationFailedException("challenge가 없거나 만료되었습니다")
        return resolve(withToken(apple.verify(identityToken, nonce).copy(displayName = displayName(displayName)), authorizationCode))
    }

    // 웹 방식(Android): 앱이 만든 verifier의 SHA-256(base64url)을 받아 두고 애플 인가 주소를 준다. 콜백 결과는 그 verifier를 가진 앱만 바꿔 갈 수 있다
    fun startWeb(verifierHash: String): String {
        requireConfigured()
        if (!VERIFIER_HASH.matches(verifierHash)) throw InvalidInputException("verifierHash 형식이 올바르지 않습니다")
        val nonce = random()
        val state = states.issue(AppleSignInStep.NONCE, AppleSignInState(nonce = nonce, verifierHash = verifierHash))
        return apple.authorizeUrl(state, nonce)
    }

    // 애플이 form_post로 돌려준 결과를 확인하고 앱으로 돌려보낼 주소를 만든다. 성공이면 1회용 code, 아니면 error(cancelled·failed).
    // 토큰은 주소에 싣지 않는다. 브라우저가 보낸 요청이라 실패도 예외 대신 앱 주소로 알린다
    fun completeWeb(state: String?, identityToken: String?, displayName: String?, error: String?, authorizationCode: String? = null): URI {
        if (error != null || state == null || identityToken == null) {
            return apple.appRedirect("error", if (error == "user_cancelled_authorize") "cancelled" else "failed")
        }
        return try {
            requireConfigured()
            val started = states.consume(UUID.fromString(state), AppleSignInStep.NONCE)?.takeIf { it.verifierHash != null }
                ?: throw AuthenticationFailedException("로그인 요청이 없거나 만료되었습니다")
            val identity = withToken(apple.verify(identityToken, started.nonce!!).copy(displayName = displayName(displayName)), authorizationCode)
            val code = states.issue(AppleSignInStep.CALLBACK, AppleSignInState(verifierHash = started.verifierHash, identity = identity))
            apple.appRedirect("code", code.toString())
        } catch (e: AuthenticationFailedException) {
            log.info("Apple callback rejected: {}", e.message)
            apple.appRedirect("error", "failed")
        } catch (e: ServiceUnavailableException) {
            log.info("Apple callback rejected: {}", e.message)
            apple.appRedirect("error", "failed")
        } catch (_: IllegalArgumentException) {
            apple.appRedirect("error", "failed")
        }
    }

    fun exchangeWeb(code: UUID, verifier: String): AppleSignInResult {
        requireConfigured()
        val callback = states.consume(code, AppleSignInStep.CALLBACK)
            ?: throw AuthenticationFailedException("로그인 결과가 없거나 만료되었습니다")
        if (!MessageDigest.isEqual(sha256(verifier).toByteArray(), callback.verifierHash!!.toByteArray())) {
            throw AuthenticationFailedException("로그인 결과를 확인하지 못했습니다")
        }
        return resolve(callback.identity!!)
    }

    // 새 계정을 만든다. 같은 애플 계정으로 동시에 가입하면 먼저 만든 계정으로 로그인한다
    fun register(linkToken: UUID): TokenPair {
        val identity = consumeLink(linkToken)
        val created = try {
            transaction.execute {
                val user = User(UUID.randomUUID(), listOf(UserRole.USER))
                users.create(user)
                identities.create(PROVIDER, identity.subject, user.id, identity.email, identity.displayName, identity.clientId, identity.refreshToken)
                user
            }!!
        } catch (_: DuplicateKeyException) {
            existingUser(identity) ?: throw AuthenticationFailedException("애플 계정을 연결하지 못했습니다")
        }
        return tokens.issue(created)
    }

    // 로그인한 사용자(패스키 계정)에 애플 계정을 연결한다. 이미 그 사용자에 연결돼 있으면 그대로 성공
    fun link(userId: UserId, linkToken: UUID) {
        val identity = consumeLink(linkToken)
        val owner = identities.findUserId(PROVIDER, identity.subject)
        if (owner == userId) return
        if (owner != null) throw InvalidInputException("이 애플 계정은 다른 Dearby 계정에 연결되어 있습니다")
        if (identities.existsForUser(PROVIDER, userId)) throw InvalidInputException("이 계정에는 이미 다른 애플 계정이 연결되어 있습니다")
        users.findById(userId) ?: throw AuthenticationFailedException("계정을 찾을 수 없습니다")
        try {
            identities.create(PROVIDER, identity.subject, userId, identity.email, identity.displayName, identity.clientId, identity.refreshToken)
        } catch (_: DuplicateKeyException) {
            if (identities.findUserId(PROVIDER, identity.subject) != userId) {
                throw InvalidInputException("이 애플 계정은 다른 Dearby 계정에 연결되어 있습니다")
            }
        }
    }

    private fun resolve(identity: AppleIdentity): AppleSignInResult {
        val user = existingUser(identity)
            ?: return AppleSignInResult.NeedsAccount(states.issue(AppleSignInStep.LINK, AppleSignInState(identity = identity)), identity.email)
        if (identity.clientId != null && identity.refreshToken != null) {
            identities.updateToken(PROVIDER, identity.subject, identity.clientId, identity.refreshToken)
        }
        return AppleSignInResult.SignedIn(tokens.issue(user))
    }

    private fun withToken(identity: AppleIdentity, authorizationCode: String?): AppleIdentity {
        val code = authorizationCode?.takeIf { it.isNotBlank() } ?: return identity
        val clientId = identity.clientId ?: return identity
        return identity.copy(refreshToken = appleTokens.refreshToken(code, clientId))
    }

    private fun existingUser(identity: AppleIdentity): User? =
        identities.findUserId(PROVIDER, identity.subject)?.let(users::findById)

    private fun consumeLink(linkToken: UUID): AppleIdentity =
        states.consume(linkToken, AppleSignInStep.LINK)?.identity
            ?: throw AuthenticationFailedException("애플 로그인이 만료되었습니다. 다시 시도해 주세요")

    private fun requireConfigured() {
        if (!apple.isConfigured()) throw ServiceUnavailableException("애플 로그인을 지금 쓸 수 없습니다")
    }

    private fun displayName(name: String?): String? =
        name?.trim()?.take(MAX_DISPLAY_NAME)?.ifEmpty { null }

    private fun random(): String {
        val bytes = ByteArray(32).also(SecureRandom()::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        const val PROVIDER = "APPLE"
        private const val MAX_DISPLAY_NAME = 200
        // SHA-256 base64url(패딩 없음)은 43자
        private val VERIFIER_HASH = Regex("^[A-Za-z0-9_-]{43}$")

        fun sha256(value: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))
    }
}
