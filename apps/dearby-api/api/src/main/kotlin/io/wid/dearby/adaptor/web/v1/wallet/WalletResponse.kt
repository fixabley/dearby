package io.wid.dearby.adaptor.web.v1.wallet

data class WalletResponse(val items: List<ReceiptResponse>, val shares: List<WalletShareResponse>)
