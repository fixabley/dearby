package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.domain.profile.Role

// admin-v1.md "이력 검토·역할"의 Role. owner는 역할이 귀속된 조직·프로그램·활동, source는 curated|collected
data class RoleResponse(val id: Long, val name: String, val nameEn: String?, val owner: OwnerResponse, val source: String) {
    data class OwnerResponse(val kind: String, val id: Long, val name: String)

    companion object {
        fun from(role: Role) = RoleResponse(role.id, role.name, role.nameEn, OwnerResponse(role.owner.kind.name.lowercase(), role.owner.id!!, role.owner.name), role.source.name.lowercase())
    }
}
