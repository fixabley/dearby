package io.wid.dearby.domain.collection

// 수집 초안 저장 결과. 게시된 활동은 관리자 검토를 거쳤고, 삭제된 활동은 관리자가 버렸으므로 둘 다 다시 쓰지 않는다
enum class DraftSaveResult { SAVED, SKIPPED_PUBLISHED, SKIPPED_DISMISSED }
