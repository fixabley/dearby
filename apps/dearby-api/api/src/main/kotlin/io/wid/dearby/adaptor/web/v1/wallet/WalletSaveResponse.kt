package io.wid.dearby.adaptor.web.v1.wallet

import java.util.*

data class WalletSaveResponse(val receiptId: UUID, val cardId: UUID, val shareId: UUID, val status: String)
