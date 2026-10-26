package io.wid.dearby.application.profile

import io.wid.dearby.domain.ContactKind
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.RateLimitedException
import io.wid.dearby.domain.ServiceUnavailableException
import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

// 이메일 인증번호로 연락처 이메일 항목을 인증(native-v1.md "연락처 인증"): 6자리 숫자, 10분, 같은 이메일 1분당 1회·하루 10회, 5번 틀리면 폐기
@Service
class EmailContactVerificationService(
    private val mail: VerificationMailSender?, // api 모듈에만 구현이 있다. 없으면(어드민) 503
    private val challenges: EmailContactChallengeRepository,
    private val profiles: ProfileService,
) {

    // 메일을 보내지 못하면 롤백돼 발송 횟수에 세지 않는다
    @Transactional
    fun challenge(userId: UserId, email: String): EmailContactChallenge {
        val mail = requireConfigured()
        val address = email.trim()
        if (address.length > MAX_EMAIL || !ProfileService.EMAIL.matches(address)) throw InvalidInputException("이메일 형식이 올바르지 않습니다")
        profiles.requireConsent(userId)
        if (profiles.get(userId).version == 0) throw InvalidInputException("프로필을 먼저 저장해 주세요", "Profile missing for email challenge: $userId", code = ProfileService.PROFILE_REQUIRED)
        val now = now()
        if (challenges.countSince(address, now.minus(PER_MINUTE)) >= 1) throw RateLimitedException("인증번호는 1분에 한 번 받을 수 있어요")
        if (challenges.countSince(address, now.minus(PER_DAY)) >= MAX_PER_DAY) throw RateLimitedException("오늘 이 이메일로 받을 수 있는 인증번호를 모두 썼어요. 내일 다시 시도해 주세요")
        val id = UUID.randomUUID()
        val code = "%06d".format(SecureRandom().nextInt(1_000_000))
        val challenge = EmailContactChallenge(id, userId, address, hash(id, code), now, now.plus(TTL))
        challenges.create(challenge)
        mail.sendCode(address, code)
        return challenge
    }

    // 틀린 시도 수·폐기는 400을 돌려줘도 남아야 하므로 InvalidInputException에는 롤백하지 않는다
    @Transactional(noRollbackFor = [InvalidInputException::class])
    fun verify(userId: UserId, challengeId: UUID, code: String): Profile {
        requireConfigured()
        val challenge = challenges.find(challengeId)?.takeIf { it.userId == userId }
            ?: throw InvalidInputException("인증번호가 만료되었어요. 다시 받아 주세요", "Email challenge missing or not owned: $challengeId", code = CHALLENGE_EXPIRED)
        if (!MessageDigest.isEqual(hash(challenge.id, code.trim()).toByteArray(), challenge.codeHash.toByteArray())) {
            val attempts = challenges.fail(challenge.id)
            if (attempts >= MAX_ATTEMPTS) {
                challenges.consume(challenge.id, now())
                throw InvalidInputException("인증번호를 ${MAX_ATTEMPTS}번 잘못 입력해 폐기했어요. 다시 받아 주세요", "Email challenge discarded: $challengeId", code = CHALLENGE_EXPIRED)
            }
            throw InvalidInputException("인증번호가 맞지 않아요. ${MAX_ATTEMPTS - attempts}번 더 입력할 수 있어요", "Email code mismatch: $challengeId", code = CODE_MISMATCH)
        }
        challenges.consume(challenge.id, now())
        return profiles.markContactVerified(userId, ContactKind.EMAIL, challenge.email)
    }

    private fun requireConfigured(): VerificationMailSender =
        mail?.takeIf { it.isConfigured() } ?: throw ServiceUnavailableException("이메일 인증을 지금 쓸 수 없습니다", "dearby.mail not configured")

    // DB(timestamptz)는 마이크로초까지라 응답(expiresAt)도 저장값과 같게 자른다
    private fun now() = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(java.time.temporal.ChronoUnit.MICROS)

    companion object {
        const val CHALLENGE_EXPIRED = "CHALLENGE_EXPIRED"
        const val CODE_MISMATCH = "CODE_MISMATCH"
        const val MAX_ATTEMPTS = 5
        const val MAX_PER_DAY = 10
        val TTL: Duration = Duration.ofMinutes(10)
        private val PER_MINUTE = Duration.ofMinutes(1)
        private val PER_DAY = Duration.ofHours(24)
        private const val MAX_EMAIL = 320

        // 코드 공간이 작아 challenge id를 소금으로 섞는다. DB를 읽어도 코드가 바로 보이지 않게 하는 정도
        fun hash(id: UUID, code: String): String =
            MessageDigest.getInstance("SHA-256").digest("$id:$code".toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
