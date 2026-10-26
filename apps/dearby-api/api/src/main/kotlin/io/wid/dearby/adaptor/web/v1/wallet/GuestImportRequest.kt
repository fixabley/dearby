package io.wid.dearby.adaptor.web.v1.wallet

// POST /v1/wallet/import-guest(native-v1.md "웹 로그인"). guestToken은 웹 게스트 쿠키의 값으로 Next 서버만 안다
data class GuestImportRequest(val guestToken: String = "")

// imported는 새로 만든 Receipt 수(이미 있던 것·내 명함·차단한 주인의 명함은 세지 않는다)
data class GuestImportResponse(val imported: Int)
