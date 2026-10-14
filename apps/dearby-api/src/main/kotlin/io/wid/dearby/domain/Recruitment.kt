package io.wid.dearby.domain

// 응답 시각 기준 모집 상태. Activity.recruitmentAt이 저장된 값과 시각으로 계산한다
data class Recruitment(val status: RecruitmentStatus, val freshness: Freshness, val isRecruiting: Boolean)
