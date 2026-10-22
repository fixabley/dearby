package io.wid.dearby.adaptor.web.v1.wallet

// PUT /v1/wallet/cards/:cardId. activityId·code는 QR 주소의 activity·code(없으면 null)
data class WalletSaveRequest(val activityId: Long? = null, val code: String? = null)
