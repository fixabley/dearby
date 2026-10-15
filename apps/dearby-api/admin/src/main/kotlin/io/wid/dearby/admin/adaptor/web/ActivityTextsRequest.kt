package io.wid.dearby.admin.adaptor.web

// 보낸 필드만 바꾼다. 영문 값은 빈 문자열이면 지운다
data class ActivityTextsRequest(
    val name: String? = null,
    val nameEn: String? = null,
    val description: String? = null,
    val descriptionEn: String? = null,
)
