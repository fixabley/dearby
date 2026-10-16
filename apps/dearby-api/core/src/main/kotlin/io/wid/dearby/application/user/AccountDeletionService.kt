package io.wid.dearby.application.user

import io.wid.dearby.application.auth.AppleSignInService
import io.wid.dearby.application.auth.AppleTokenClient
import io.wid.dearby.domain.UserId
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 계정 삭제(native-v1.md "계정 삭제"). 되돌릴 수 없다. 애플 토큰 폐기는 먼저 시도하고, 실패해도 삭제는 진행한다
@Service
class AccountDeletionService(
    private val users: UserRepository,
    private val identities: UserIdentityRepository,
    private val appleTokens: AppleTokenClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun delete(userId: UserId) {
        identities.findAllByUser(userId).filter { it.provider == AppleSignInService.PROVIDER }.forEach {
            val revoked = it.refreshToken != null && it.clientId != null && appleTokens.revoke(it.refreshToken, it.clientId)
            if (!revoked) log.warn("Apple token not revoked for user {} (token {})", userId, if (it.refreshToken == null) "missing" else "rejected")
        }
        users.delete(userId)
    }
}
