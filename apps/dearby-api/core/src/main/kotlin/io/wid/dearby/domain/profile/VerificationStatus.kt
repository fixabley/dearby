package io.wid.dearby.domain.profile

// 인증자료 확인. VERIFIED가 아니면 앱이 미확인 배지를 붙인다. 미인증이어도 명함에 넣을 수 있다
enum class VerificationStatus { UNVERIFIED, PENDING, VERIFIED, REJECTED }
