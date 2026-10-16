package io.wid.dearby.domain.profile

// 직접 입력한 조직·프로그램·활동·역할의 관리자 승인. APPROVED만 명함에 넣을 수 있다
enum class ApprovalStatus { APPROVED, PENDING, REJECTED }
