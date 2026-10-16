package io.wid.dearby.adaptor.web.v1.profile

// PUT /v1/profile: 전체를 바꾼다
data class ProfileRequest(
    val name: String,
    val job: String = "",
    val introduction: String = "",
    val contacts: List<ContactRequest> = emptyList(),
    val histories: List<HistoryRequest> = emptyList(),
)
