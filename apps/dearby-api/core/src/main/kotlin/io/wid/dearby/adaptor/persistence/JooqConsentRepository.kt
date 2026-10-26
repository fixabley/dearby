package io.wid.dearby.adaptor.persistence

import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_AD_CONSENTS
import io.wid.dearby.adaptor.persistence.jooq.tables.references.USER_CONSENTS
import io.wid.dearby.application.profile.ConsentRepository
import io.wid.dearby.domain.profile.AdConsent
import io.wid.dearby.domain.profile.Consent
import io.wid.dearby.domain.UserId
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class JooqConsentRepository(private val dsl: DSLContext) : ConsentRepository {

    override fun find(userId: UserId): Consent? =
        dsl.selectFrom(USER_CONSENTS).where(USER_CONSENTS.USER_ID.eq(userId)).fetchOne()
            ?.let { Consent(it.privacyVersion!!, it.termsVersion!!, it.consentedAt!!) }

    override fun save(userId: UserId, consent: Consent) {
        dsl.insertInto(USER_CONSENTS)
            .set(USER_CONSENTS.USER_ID, userId)
            .set(USER_CONSENTS.PRIVACY_VERSION, consent.privacyVersion)
            .set(USER_CONSENTS.TERMS_VERSION, consent.termsVersion)
            .set(USER_CONSENTS.CONSENTED_AT, consent.consentedAt)
            .onConflict(USER_CONSENTS.USER_ID).doUpdate()
            .set(USER_CONSENTS.PRIVACY_VERSION, consent.privacyVersion)
            .set(USER_CONSENTS.TERMS_VERSION, consent.termsVersion)
            .set(USER_CONSENTS.CONSENTED_AT, consent.consentedAt)
            .execute()
    }

    override fun findAd(userId: UserId): AdConsent? =
        dsl.selectFrom(USER_AD_CONSENTS).where(USER_AD_CONSENTS.USER_ID.eq(userId)).fetchOne()
            ?.let { AdConsent(it.version!!, it.consentedAt!!) }

    override fun saveAd(userId: UserId, consent: AdConsent) {
        dsl.insertInto(USER_AD_CONSENTS)
            .set(USER_AD_CONSENTS.USER_ID, userId)
            .set(USER_AD_CONSENTS.VERSION, consent.version)
            .set(USER_AD_CONSENTS.CONSENTED_AT, consent.consentedAt)
            .onConflict(USER_AD_CONSENTS.USER_ID).doUpdate()
            .set(USER_AD_CONSENTS.VERSION, consent.version)
            .set(USER_AD_CONSENTS.CONSENTED_AT, consent.consentedAt)
            .execute()
    }

    override fun deleteAd(userId: UserId) {
        dsl.deleteFrom(USER_AD_CONSENTS).where(USER_AD_CONSENTS.USER_ID.eq(userId)).execute()
    }
}
