package io.wid.dearby.application.profile

import io.wid.dearby.domain.UserId
import io.wid.dearby.domain.profile.History

// 관리자 화면에는 profileName만 보여 준다(계정 id는 싣지 않는다). userId는 파일 접근 확인용
data class HistoryForReview(val history: History, val userId: UserId, val profileName: String)
