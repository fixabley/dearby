package io.wid.dearby.domain.curation

// 공식 안내 확인 상태. VERIFIED: 확인 시각부터 validUntil 전까지 유효, STALE: 확인이 오래됨, UNAVAILABLE: 마지막 확인에 실패
enum class Freshness { VERIFIED, STALE, UNAVAILABLE }
