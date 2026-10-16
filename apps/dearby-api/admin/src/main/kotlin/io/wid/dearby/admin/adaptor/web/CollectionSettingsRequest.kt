package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.curation.CollectionSettings

data class CollectionSettingsRequest(
    val officialUrl: String? = null,
    val allowedHosts: List<String> = emptyList(),
    val intervalDays: Int = 1,
    val enabled: Boolean = true,
) {
    fun toSettings(): CollectionSettings {
        if (intervalDays < 1) throw InvalidInputException("수집 주기는 1일 이상이어야 합니다")
        return CollectionSettings(officialUrl, allowedHosts, intervalDays, enabled)
    }
}
