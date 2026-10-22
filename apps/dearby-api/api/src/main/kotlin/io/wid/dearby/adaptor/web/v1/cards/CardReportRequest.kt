package io.wid.dearby.adaptor.web.v1.cards

// POST /v1/cards/:id/reports. reason: SPAM|IMPERSONATION|INAPPROPRIATE|OTHER(OTHER는 detail 필수), code는 QR 코드로만 본 사람
data class CardReportRequest(val reason: String? = null, val detail: String? = null, val code: String? = null)
