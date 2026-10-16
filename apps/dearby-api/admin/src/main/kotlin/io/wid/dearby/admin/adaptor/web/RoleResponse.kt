package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.domain.profile.Role

data class RoleResponse(val id: Long, val name: String, val nameEn: String?) {
    companion object {
        fun from(role: Role) = RoleResponse(role.id, role.name, role.nameEn)
    }
}
