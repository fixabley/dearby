package io.wid.dearby.domain.collection

import io.wid.dearby.domain.ParticipationType
import io.wid.dearby.domain.eligibility.EligibilityGroup

// 같은 프로그램의 가장 최근 다른 활동(게시 우선, 없으면 최신 초안)에서 가져올 수 있는 값(admin-v1.md "이전 기수 정보 자동완성").
// 기간·장소·비용·신청 링크처럼 기수마다 바뀌는 값은 없다
data class PreviousEdition(
    val name: String,
    val audience: String?,
    val eligibility: EligibilityGroup?,
    val participationType: ParticipationType,
)
