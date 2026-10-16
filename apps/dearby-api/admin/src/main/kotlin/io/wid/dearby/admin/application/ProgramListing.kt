package io.wid.dearby.admin.application

import io.wid.dearby.domain.Organization
import io.wid.dearby.domain.Program
import io.wid.dearby.domain.curation.CollectionSettings
import java.time.LocalDate

// configured: 설정 행이 있는지(없으면 settings는 기본값)
data class ProgramListing(
    val program: Program,
    val organization: Organization?,
    val settings: CollectionSettings,
    val configured: Boolean,
    val lastSucceededOn: LocalDate?,
)
