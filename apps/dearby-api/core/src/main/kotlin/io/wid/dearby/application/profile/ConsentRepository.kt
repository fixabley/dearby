package io.wid.dearby.application.profile

import io.wid.dearby.domain.profile.Consent
import io.wid.dearby.domain.UserId

interface ConsentRepository {
    fun find(userId: UserId): Consent?
    // 있으면 덮어쓴다(다시 동의)
    fun save(userId: UserId, consent: Consent)
}
