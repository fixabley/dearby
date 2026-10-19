package io.wid.dearby.admin.application

import io.wid.dearby.domain.Address

// PATCH /v1/admin/activities/:id의 place: 보내지 않음(Keep) · JSON null(Clear) · 객체(Set)
sealed interface PlaceChange {
    data object Keep : PlaceChange
    data object Clear : PlaceChange
    data class Set(val place: Address) : PlaceChange
}
