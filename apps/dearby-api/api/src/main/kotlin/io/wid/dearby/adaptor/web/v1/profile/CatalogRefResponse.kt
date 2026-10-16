package io.wid.dearby.adaptor.web.v1.profile

import io.wid.dearby.domain.profile.CatalogRef

data class CatalogRefResponse(val id: Long, val name: String) {
    companion object {
        fun from(ref: CatalogRef) = CatalogRefResponse(ref.id, ref.name)
    }
}
