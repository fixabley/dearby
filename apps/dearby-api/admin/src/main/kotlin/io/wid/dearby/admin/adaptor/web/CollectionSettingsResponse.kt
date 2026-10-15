package io.wid.dearby.admin.adaptor.web

import io.wid.dearby.admin.application.ProgramListing
import java.time.LocalDate

// officialOnly가 false면 매일 밤 웹 전체를 검색한다(공식 출처가 아닌 페이지가 섞이고 토큰이 더 든다)
data class CollectionSettingsResponse(
    val configured: Boolean,
    val officialUrl: String?,
    val allowedHosts: List<String>,
    val intervalDays: Int,
    val enabled: Boolean,
    val officialOnly: Boolean,
    val lastSucceededOn: LocalDate?,
) {
    companion object {
        fun from(listing: ProgramListing) = with(listing.settings) {
            CollectionSettingsResponse(
                listing.configured, officialUrl, allowedHosts, intervalDays, enabled, officialOnly, listing.lastSucceededOn,
            )
        }
    }
}
