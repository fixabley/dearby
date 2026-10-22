package io.wid.dearby.adaptor.web.v1.guest

// PUT /v1/guest/cards/:id 본문(선택). activityId·code는 QR 주소의 activity·code
data class GuestSaveRequest(val activityId: Long? = null, val code: String? = null)
