package io.wid.dearby.admin.application

import io.wid.dearby.domain.eligibility.EligibilityGroup

// PATCH /v1/admin/activities/:id의 eligibility: 보내지 않음(Keep) · JSON null(Clear) · 객체(Set)
sealed interface EligibilityChange {
    data object Keep : EligibilityChange
    data object Clear : EligibilityChange
    data class Set(val group: EligibilityGroup) : EligibilityChange
}
