package io.wid.dearby.adaptor.web.v1.wallet

import java.util.*

// POST /v1/wallet/blocks·/v1/guest/blocks. 차단할 사람은 이 명함의 주인
data class BlockRequest(val cardId: UUID)
