package io.wid.dearby.domain.curation

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ParticipationType
import io.wid.dearby.domain.PublicationStatus
import java.time.OffsetDateTime

// 운영자용 활동 정보. 공개 Activity와 같은 activities 행(+activity_roles)에 저장하지만 공개 API는 내보내지 않는다.
// 같은 DB를 쓰는 운영자(admin) 모듈이 다룬다
data class ActivityCuration(
    val activityId: Id,
    val publicationStatus: PublicationStatus, // DRAFT는 공개 API에 보이지 않는다
    val officialSource: Boolean, // 출처가 프로그램의 공식 도메인인지. 웹 전체 검색에서 나온 초안은 false
    val participationType: ParticipationType,
    val recruitmentStatus: RecruitmentStatus,
    val location: String?, // 장소 문구(좌표 없음)
    val cost: String?,
    val audience: String?,
    val qualification: Map<String, List<String>>, // 자격요건. 조건 이름 → 값들. 없으면 빈 맵
    val roles: List<String>, // 모집 역할 문구(순서 유지)
    val applicationUrl: String?, // 신청 링크
    val sourceCheckedAt: OffsetDateTime?, // 공식 안내를 마지막으로 확인한 시각
    val validUntil: OffsetDateTime?, // 확인 내용이 유효한 끝 시각
    val freshness: Freshness,
    val sourceNote: String, // 안내 문구
    val inheritedFields: List<String> = emptyList(), // 이전 기수에서 가져온 필드 이름(admin-v1.md "이전 기수 정보 자동완성")
    val curatedFields: List<String> = emptyList(), // 어드민이 고친 필드 이름. 수집기는 이 필드만 덮어쓰지 않는다(admin-v1.md "운영자 지정 필드")
)
