package io.wid.dearby.domain.curation

// 운영자가 고른 모집 상태. CLOSED는 모집 기간과 상관없이 마감(조기 마감)
enum class RecruitmentStatus { OPEN, SCHEDULED, CLOSED, UNKNOWN }
