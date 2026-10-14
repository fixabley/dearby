package io.wid.dearby.domain

// 운영자가 고른 모집 상태. 모집 기간이 있으면 응답 시각 기준으로 다시 계산하고, 기간이 없을 때만 이 값을 쓴다. CLOSED는 항상 우선(조기 마감)
enum class RecruitmentStatus { OPEN, SCHEDULED, CLOSED, UNKNOWN }
