package io.wid.dearby.adaptor.web.v1.cards

import java.time.OffsetDateTime
import java.util.*

// 신고 결과(201 새 신고, 200 24시간 안 같은 신고)
data class CardReportResponse(val id: UUID, val reason: String, val createdAt: OffsetDateTime)
