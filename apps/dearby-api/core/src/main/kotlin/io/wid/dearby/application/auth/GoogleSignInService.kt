package io.wid.dearby.application.auth

import io.wid.dearby.application.auth.AppleSignInService.Companion.VERIFIER_HASH
import io.wid.dearby.application.auth.AppleSignInService.Companion.sha256
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

// 구글 로그인(native-v1.md "구글 로그인"). iOS·Android 모두 애플의 Android 방식과 같은 웹(서버 코드) 흐름이다.
// 이미 연결된 구글 계정이면 바로 로그인하고, 처음 보면 linkToken을 주어 새 계정 또는 패스키 계정 연결을 고르게 한다. 이메일로 계정을 합치지 않는다.
// 중간 상태는 애플과 같은 표(provider = GOOGLE)에 둔다. refresh token은 받지 않으므로 계정 삭제 때 폐기할 구글 토큰도 없다
@Service
class GoogleSignInService(
    private val google: GoogleIdProvider,
    private val googleTokens: GoogleTokenClient,
    private val states: AppleSignInStateRepository,
    private val identities: UserIdentityRepository,
    private val users: UserRepository,
    private val tokens: TokenService,
    private val transaction: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 앱이 만든 verifier의 SHA-256(base64url)을 받아 두고 구글 인가 주소를 준다. 콜백 결과는 그 verifier를 가진 앱만 바꿔 갈 수 있다
    fun startWeb(verifierHash: String): String {
        requireConfigured()
        if (!VERIFIER_HASH.matches(verifierHash)) throw InvalidInputException("verifierHash 형식이 올바르지 않습니다")
        val nonce = random()
        val state = states.issue(AppleSignInStep.NONCE, AppleSignInState(nonce = nonce, verifierHash = verifierHash, provider = PROVIDER))
        return google.authorizeUrl(state, nonce)
    }

    // 구글이 리디렉션으로 돌려준 결과를 확인하고 앱으로 돌려보낼 주소를 만든다. 성공이면 1회용 code, 아니면 error(cancelled·failed).
    // 토큰은 주소에 싣지 않는다. 브라우저가 보낸 요청이라 실패도 예외 대신 앱 주소로 알린다
    fun completeWeb(state: String?, code: String?, error: String?): URI {
        if (error != null || state == null || code == null) {
            return google.appRedirect("error", if (error == "access_denied") "cancelled" else "failed")
        }
        return try {
            requireConfigured()
            val started = states.consume(UUID.fromString(state), AppleSignInStep.NONCE, PROVIDER)?.takeIf { it.verifierHash != null }
                ?: throw AuthenticationFailedException("로그인 요청이 없거나 만료되었습니다")
            val identity = google.verify(googleTokens.exchange(code), started.nonce!!)
            val result = states.issue(AppleSignInStep.CALLBACK, AppleSignInState(verifierHash = started.verifierHash, identity = identity, provider = PROVIDER))
            google.appRedirect("code", result.toString())
        } catch (e: AuthenticationFailedException) {
            log.info("Google callback rejected: {}", e.message)
            google.appRedirect("error", "failed")
        } catch (e: ServiceUnavailableException) {
            log.info("Google callback rejected: {}", e.message)
            google.appRedirect("error", "failed")
        } catch (_: IllegalArgumentException) {
            google.appRedirect("error", "failed")
        }
    }

    fun exchangeWeb(code: UUID, verifier: String): GoogleSignInResult {
        requireConfigured()
        val callback = states.consume(code, AppleSignInStep.CALLBACK, PROVIDER)
            ?: throw AuthenticationFailedException("로그인 결과가 없거나 만료되었습니다")
        if (!MessageDigest.isEqual(sha256(verifier).toByteArray(), callback.verifierHash!!.toByteArray())) {
            throw AuthenticationFailedException("로그인 결과를 확인하지 못했습니다")
        }
        return resolve(callback.identity!!)
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
    }
}
