package io.wid.dearby.adaptor.web.v1.activityinfo

import io.wid.dearby.domain.profile.Role

// native-v1.md "이력 (History) — 역할 매칭"의 Role. owner는 역할이 귀속된 조직·프로그램·활동
data class RoleResponse(val id: Long, val name: String, val nameEn: String?, val owner: ActivityInfoOwnerResponse, val source: String) {
    companion object {
        fun from(role: Role) = RoleResponse(role.id, role.name, role.nameEn, ActivityInfoOwnerResponse(role.owner.kind.name.lowercase(), role.owner.id!!, role.owner.name), role.source.name.lowercase())
    }
}

data class ActivityInfoOwnerResponse(val kind: String, val id: Long, val name: String)

data class RolePageResponse(val items: List<RoleResponse>, val nextCursor: Long?)
