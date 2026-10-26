package io.wid.dearby.adaptor.web.v1.profile

// PUT /v1/profile: 전체를 바꾼다
data class ProfileRequest(
    val name: String,
    val jobs: List<ProfileJobRequest> = emptyList(),
    // 2026-10-26 소개 글 폐지(native-v1.md "소개 링크·소개 글 폐지"): 옛 앱이 보내므로 받기만 하고 쓰지 않는다
    @Suppress("unused") val introduction: String? = null,
    val introLink: String? = null,
    val contacts: List<ContactRequest> = emptyList(),
    val histories: List<HistoryRequest> = emptyList(),
)
