package io.wid.dearby.admin.adaptor.web

// 만들 때 name 필수. PATCH는 보낸 값만 바꾸고 nameEn은 빈 문자열이면 지운다
data class RoleRequest(val name: String? = null, val nameEn: String? = null)
