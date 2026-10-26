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
    // 2026-10-26 폐지 예정(소개 글 폐지): 옛 앱 호환으로 늘 빈 문자열
    val introduction: String,
    val introLink: String?,
    val contacts: List<ContactResponse>,
    val histories: List<HistoryResponse>,
    val version: Int,
    val updatedAt: OffsetDateTime,
    // 개인정보 동의(native-v1.md "개인정보 동의"): 기록이 없거나 버전이 다르면 consentRequired = true, 그동안 프로필 쓰기는 403 CONSENT_REQUIRED
    val consent: ConsentResponse? = null,
    val consentRequired: Boolean = false,
    // 프로필 사진(native-v1.md "프로필 사진"): "/v1/profile/photo?v=<바뀐 시각 ms>" 또는 null. 버전 스냅샷에는 싣지 않는다
    val photoUrl: String? = null,
) {
    companion object {
        // 버전 이력의 스냅샷(VersionResponse)은 동의 상태를 싣지 않는다(status = null)
        fun from(p: Profile, consent: ConsentStatus? = null, photoUrl: String? = null) = ProfileResponse(
            p.userId, p.name, p.jobs.map(ProfileJobResponse::from), p.jobTitle, "", p.introLink,
            p.contacts.map(ContactResponse::from), p.histories.map(HistoryResponse::from), p.version, p.updatedAt,
            consent?.consent?.let(ConsentResponse::from), consent?.required ?: false, photoUrl,
        )

        // 바뀔 때마다 v가 달라 오래 캐시해도 된다(ProfileController·ContactVerificationController가 같이 쓴다)
        fun photoUrl(updatedAt: OffsetDateTime) = "/v1/profile/photo?v=${updatedAt.toInstant().toEpochMilli()}"
    }
}
