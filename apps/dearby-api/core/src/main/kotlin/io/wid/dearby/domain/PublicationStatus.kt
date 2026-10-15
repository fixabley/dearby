package io.wid.dearby.domain

// 게시 상태. 수집한 활동은 DRAFT로 들어오고, 관리자가 PUBLISHED로 바꿔야 공개 API에 보인다
enum class PublicationStatus { DRAFT, PUBLISHED }
