package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.domain.profile.ConsentStatus
import io.wid.dearby.domain.profile.Profile
import java.time.OffsetDateTime
import java.util.*

// id는 사용자 id. version은 If-Match에 쓴다. job은 승인된 직무 이름을 이은 읽기 전용 값(명함의 job과 같다)
data class ProfileResponse(
    val id: UUID,
    val name: String,
    val jobs: List<ProfileJobResponse>,
    val job: String,
    val introduction: String,
    val contacts: List<ContactResponse>,
    val histories: List<HistoryResponse>,
    val version: Int,
    val updatedAt: OffsetDateTime,
    // 개인정보 동의(native-v1.md "개인정보 동의"): 기록이 없거나 버전이 다르면 consentRequired = true, 그동안 프로필 쓰기는 403 CONSENT_REQUIRED
    val consent: ConsentResponse? = null,
    val consentRequired: Boolean = false,
) {
    companion object {
        // 버전 이력의 스냅샷(VersionResponse)은 동의 상태를 싣지 않는다(status = null)
        fun from(p: Profile, consent: ConsentStatus? = null) = ProfileResponse(
            p.userId, p.name, p.jobs.map(ProfileJobResponse::from), p.jobTitle, p.introduction,
            p.contacts.map(ContactResponse::from), p.histories.map(HistoryResponse::from), p.version, p.updatedAt,
            consent?.consent?.let(ConsentResponse::from), consent?.required ?: false,
        )
    }
}
