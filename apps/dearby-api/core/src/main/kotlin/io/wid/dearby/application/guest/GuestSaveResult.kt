package io.wid.dearby.application.guest

import java.util.*

// 저장 결과. guestToken은 토큰 없이 처음 저장해 세션이 생겼을 때만(201) 있고, Next 서버만 쿠키로 소비한다
data class GuestSaveResult(val cardId: UUID, val status: String, val guestToken: String?)
