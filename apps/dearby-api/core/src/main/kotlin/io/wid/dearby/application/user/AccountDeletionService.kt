package io.wid.dearby.application.user

import io.wid.dearby.domain.UserId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 계정 삭제(native-v1.md "계정 삭제"). 되돌릴 수 없다. 애플 토큰 폐기는 먼저 시도하고, 실패해도 삭제는 진행한다
@Service
class AccountDeletionService(
    private val users: UserRepository,
    private val signInMethods: SignInMethodService,
) {

    @Transactional
    fun delete(userId: UserId) {
        signInMethods.revokeAppleTokens(userId)
        users.delete(userId)
    }
}
