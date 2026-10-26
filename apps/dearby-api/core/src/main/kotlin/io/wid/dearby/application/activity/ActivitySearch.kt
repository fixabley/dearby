package io.wid.dearby.application.activity

import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.ActivityStatus
import io.wid.dearby.domain.DiscoveryKind
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.PublicationStatus
import io.wid.dearby.domain.eligibility.EligibilityFilter

// q는 활동 이름·설명에서 대소문자 없이 찾는다. after는 이전 페이지의 nextCursor(활동 id, 오름차순).
// publicationStatus: 공개 API는 PUBLISHED만, 관리자는 null(전부) 또는 원하는 상태
data class ActivitySearch(
    val q: String?,
    val organizationId: Id?,
    val programId: Id?,
    val after: Id?,
    val size: Int,
    val publicationStatus: PublicationStatus? = PublicationStatus.PUBLISHED,
    val eligibility: EligibilityFilter? = null, // 대상자 검색. 1차는 저장소(SQL), 2차는 서비스(JSON)
    val categories: Set<ActivityCategory>? = null, // 활동 종류 필터(native-v1.md "활동 종류"). null이면 전부
    val kind: DiscoveryKind? = null, // 프로그램·이벤트 전환(native-v1.md "발견 프로그램·이벤트 전환"). null이면 전부
    val openOnly: Boolean = false, // true면 열린 활동만(native-v1.md "열린 활동 판정 일원화", 발견 묶음과 같은 규칙)
    val tagIds: Set<Id>? = null, // 태그 필터(native-v1.md "태그"): 모두 가진 활동만(AND). null이면 전부
    val forHistory: Boolean = false, // true면 활동 이력 후보만(native-v1.md "활동 이력에서 단기 행사 제외": 7일 미만 행사 제외)
    val ids: Set<Id>? = null, // 이 활동들만(즐겨찾기). null이면 전부
    val programIds: Set<Id>? = null, // 이 프로그램들의 활동만(즐겨찾기 프로그램 알림). null이면 전부
    val statuses: Set<ActivityStatus>? = null, // 활동 상태 필터(native-v1.md "활동 상태"). null이면 전부
)
