package io.wid.dearby.admin.application

import io.wid.dearby.domain.ActivityCategory

// PATCH의 category: 생략은 유지, JSON null은 미분류로, 코드는 그 종류로(native-v1.md "활동 종류")
sealed interface CategoryChange {
    data object Keep : CategoryChange
    data object Clear : CategoryChange
    data class Set(val category: ActivityCategory) : CategoryChange
}
