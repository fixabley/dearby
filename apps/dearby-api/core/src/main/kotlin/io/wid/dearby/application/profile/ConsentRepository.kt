package io.wid.dearby.application.profile

import io.wid.dearby.domain.profile.AdConsent
import io.wid.dearby.domain.profile.Consent
import io.wid.dearby.domain.UserId

interface ConsentRepository {
    fun find(userId: UserId): Consent?
    // 있으면 덮어쓴다(다시 동의)
    fun save(userId: UserId, consent: Consent)

    // 광고 목적 제3자 제공 동의(선택). 저장은 덮어쓰고, 지우기는 없어도 된다
    fun findAd(userId: UserId): AdConsent?
    fun saveAd(userId: UserId, consent: AdConsent)
    fun deleteAd(userId: UserId)
}
