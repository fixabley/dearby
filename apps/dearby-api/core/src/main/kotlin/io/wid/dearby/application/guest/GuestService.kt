package io.wid.dearby.application.guest

import io.wid.dearby.application.card.CardQrCodes
import io.wid.dearby.application.card.CardRepository
import io.wid.dearby.application.card.CardSafetyRepository
import io.wid.dearby.application.card.ExchangeContextRepository
import io.wid.dearby.application.card.WalletService
import io.wid.dearby.domain.card.CardBlock
import io.wid.dearby.domain.card.ExchangeRef
import io.wid.dearby.domain.*
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// 웹 비로그인 저장(native-v1.md "웹 비로그인 명함 저장"·"홈 화면 세션 잇기").
// 세션은 토큰 digest만 저장하고 자동 만료가 없다. 요청 제한은 프로세스 메모리의 1분 창이다
@Service
class GuestService(
    private val guests: GuestRepository, private val cards: CardRepository, private val codes: CardQrCodes, private val safety: CardSafetyRepository,
    private val contexts: ExchangeContextRepository,
) {

    // 1분 창 카운터(재시작 때 초기화). forwarded IP는 믿지 않는다
    private var windowStart = 0L
    private val requests = AtomicInteger()
    private val creations = AtomicInteger()
    private val perSession = ConcurrentHashMap<String, AtomicInteger>()

    // 모든 게스트 요청이 먼저 거친다: 프록시 키 확인과 전체 요청 제한
    fun admit(proxySecret: String?, suppliedKey: String?) {
        if (proxySecret == null || proxySecret.length < MIN_SECRET) throw ServiceUnavailableException("게스트 저장을 지금 쓸 수 없습니다", "GUEST_PROXY_SECRET missing or short")
        if (suppliedKey == null || !MessageDigest.isEqual(digest(suppliedKey).toByteArray(), digest(proxySecret).toByteArray())) {
            throw PermissionDeniedException("서버 프록시를 거쳐야 합니다", "guest proxy key mismatch")
        }
        tick()
        if (requests.incrementAndGet() > MAX_REQUESTS) throw RateLimitedException("요청이 많습니다. 잠시 후 다시 시도해 주세요")
    }

    @Transactional(readOnly = true)
    fun wallet(token: String?): GuestWallet = guests.wallet(session(token))

    // 토큰 없이 처음 저장하면 세션을 만든다(201). 기존 토큰이면 세션을 다시 만들지 않는다.
    // 이 세션에 회수 전 저장이 없으면 유효한 QR 코드가 필요하다(native-v1.md "명함 QR 코드"). 코드 확인이 세션 만들기보다 먼저다
    @Transactional
    fun saveCard(token: String?, cardId: UUID, activityId: Id? = null, code: String? = null): GuestSaveResult {
        val card = cards.find(cardId) ?: throw NotFoundException("명함을 찾을 수 없습니다", "Card not found for guest: $cardId")
        val existing = token?.let(::session)
        if (existing != null && safety.isBlocked(null, existing, card.ownerId)) throw ConflictException(WalletService.BLOCKED_DETAIL, "guest saved blocked owner's card: $cardId", code = WalletService.BLOCKED)
        if (existing == null || !guests.hasCard(existing, cardId)) codes.require(cardId, code)
        val (digest, newToken) = if (existing != null) existing to null else sessionOrNew(null)
        // 맥락은 처음 저장할 때만 기록한다. 활동이 있으면 지금 진행 중인 블록까지 판정하고(비로그인은 찜이 없어 세션은 없다), 모르는 id는 맥락 없음(native-v1.md "명함 교환")
        val saved = addCard(digest, cardId, activityId?.let { contexts.locate(it, null, now()) } ?: ExchangeRef.NONE)
        return GuestSaveResult(cardId, if (saved) "saved" else "alreadySaved", newToken)
    }


    // 명함 조회·신고의 게스트 근거: 프록시를 거친 유효한 세션의 digest. 토큰이 없거나 없어진 세션이면 null(코드로는 볼 수 있게 401을 내지 않는다)
    @Transactional(readOnly = true)
    fun sessionDigest(proxySecret: String?, suppliedKey: String?, token: String?): String? {
        if (token == null) return null
        admit(proxySecret, suppliedKey)
        return try { session(token) } catch (_: AuthenticationFailedException) { null }
    }

    // 그 세션이 명함을 회수 전 상태로 저장해 두었는지
    @Transactional(readOnly = true)
    fun holds(digest: String?, cardId: UUID) = digest != null && guests.hasCard(digest, cardId)

    // 게스트 차단: 이 세션에서 그 주인의 저장을 모두 회수하고 기록한다(목록·해제 없음). 없는·철회된 명함 404
    @Transactional
    fun block(token: String?, cardId: UUID): CardBlock {
        val digest = session(token)
        val card = cards.find(cardId) ?: throw NotFoundException("명함을 찾을 수 없습니다", "guest block for missing card: $cardId")
        val now = now()
        guests.recallFromOwner(digest, card.ownerId, now)
        return safety.block(null, digest, card.ownerId, card.profileName, now)
    }

    // 이미 지웠거나 철회된 참조도 그대로 성공(멱등)
    @Transactional
    fun removeCard(token: String?, cardId: UUID) = guests.removeCard(session(token), cardId)

    @Transactional
    fun deleteSession(token: String?) {
        val digest = session(token)
        guests.deleteSession(digest)
        perSession.remove(digest)
    }

    // 1회용 코드(10분). 코드에서 파생한 키로 토큰을 암호화해 두므로 DB만으로는 토큰을 되살릴 수 없다
    @Transactional
    fun issueHandoff(token: String?): Pair<String, OffsetDateTime> {
        val digest = session(token)
        val code = randomToken()
        val expiresAt = now().plus(HANDOFF_LIFETIME)
        guests.clearExpiredHandoffs(now())
        guests.putHandoff(GuestHandoff(digest(code), digest, seal(token!!, code, digest), expiresAt, null))
        return code to expiresAt
    }

    // 없음·만료·사용됨·폐기는 모두 404(구분하지 않음). 성공하면 바로 사용 처리
    @Transactional
    fun redeemHandoff(code: String): String {
        if (!CODE.matches(code)) throw NotFoundException("코드를 찾을 수 없습니다", "handoff code malformed")
        val now = now()
        guests.clearExpiredHandoffs(now)
        val row = guests.findHandoff(digest(code))
        val ciphertext = row?.tokenCiphertext
        if (row == null || ciphertext == null || row.usedAt != null || !now.isBefore(row.expiresAt)) throw NotFoundException("코드를 찾을 수 없습니다", "handoff unusable")
        val token = try { unseal(ciphertext, code, row.sessionDigest) } catch (_: Exception) { throw NotFoundException("코드를 찾을 수 없습니다", "handoff unseal failed") }
        guests.markHandoffUsed(row.digest, now)
        return token
    }

    // 유효한 세션의 digest. 세션당 요청 제한도 여기서 센다
    private fun session(token: String?): String {
        if (token == null || !TOKEN.matches(token)) throw AuthenticationFailedException("게스트 세션이 없습니다", "guest token missing or malformed")
        val digest = digest(token)
        if (!guests.sessionExists(digest)) throw AuthenticationFailedException("게스트 세션이 없습니다", "guest session unknown")
        tick()
        if (perSession.computeIfAbsent(digest) { AtomicInteger() }.incrementAndGet() > MAX_PER_SESSION) throw RateLimitedException("요청이 많습니다. 잠시 후 다시 시도해 주세요")
        return digest
    }

    private fun sessionOrNew(token: String?): Pair<String, String?> {
        if (token != null) return session(token) to null
        if (guests.sessionCount() >= MAX_SESSIONS) throw ConflictException("게스트 저장 공간이 가득 찼습니다")
        tick()
        if (creations.incrementAndGet() > MAX_CREATIONS) throw RateLimitedException("요청이 많습니다. 잠시 후 다시 시도해 주세요")
        val newToken = randomToken()
        val digest = digest(newToken)
        guests.createSession(digest)
        return digest to newToken
    }

    private fun addCard(digest: String, cardId: UUID, context: ExchangeRef): Boolean {
        guests.pruneRevoked(digest)
        if (guests.hasCard(digest, cardId)) return false
        if (guests.cardCount(digest) >= MAX_CARDS) throw ConflictException("저장할 수 있는 명함 수를 넘었습니다")
        guests.addCard(digest, cardId, context)
        return true
    }

    @Synchronized
    private fun tick() {
        val time = System.currentTimeMillis()
        if (time >= windowStart + 60_000 || time < windowStart) {
            windowStart = time
            requests.set(0)
            creations.set(0)
            perSession.clear()
        }
    }

    private fun now() = OffsetDateTime.now(ZoneOffset.UTC)

    companion object {
        const val MIN_SECRET = 32
        const val MAX_REQUESTS = 1200
        const val MAX_PER_SESSION = 120
        const val MAX_CREATIONS = 60
        const val MAX_SESSIONS = 10_000
        const val MAX_CARDS = 100
        val HANDOFF_LIFETIME: Duration = Duration.ofMinutes(10)
        val TOKEN = Regex("^[A-Za-z0-9_-]{43}$")
        val CODE = Regex("^[A-Za-z0-9_-]{43,128}$")
        private val random = SecureRandom()

        fun digest(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }

        fun randomToken(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

        // HKDF-SHA256(code, salt "dearby/guest-handoff", info "v1 token key") → AES-256-GCM, AAD = 세션 digest(옛 TS 구현과 같은 형식)
        private fun key(code: String): SecretKeySpec {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec("dearby/guest-handoff".toByteArray(), "HmacSHA256"))
            val prk = mac.doFinal(code.toByteArray())
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val okm = mac.doFinal("v1 token key".toByteArray() + byteArrayOf(1))
            return SecretKeySpec(okm.copyOf(32), "AES")
        }

        fun seal(token: String, code: String, sessionDigest: String): String {
            val iv = ByteArray(12).also(random::nextBytes)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(code), GCMParameterSpec(128, iv))
            cipher.updateAAD(sessionDigest.toByteArray())
            val sealed = cipher.doFinal(token.toByteArray())
            val body = sealed.copyOfRange(0, sealed.size - 16)
            val tag = sealed.copyOfRange(sealed.size - 16, sealed.size)
            val b64 = Base64.getUrlEncoder().withoutPadding()
            return listOf(iv, body, tag).joinToString(".") { b64.encodeToString(it) }
        }

        fun unseal(sealed: String, code: String, sessionDigest: String): String {
            val (iv, body, tag) = sealed.split(".").map { Base64.getUrlDecoder().decode(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(code), GCMParameterSpec(128, iv))
            cipher.updateAAD(sessionDigest.toByteArray())
            return String(cipher.doFinal(body + tag))
        }
    }
}
