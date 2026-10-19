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
    val previousRound: Int?, // 가장 최근 활동 이름에서 읽은 기수 번호(admin-v1.md "주소 토큰"), 없으면 null
    val previewUrls: List<String>, // 지금 수집하면 볼 시작 주소(토큰 펼친 결과)
    val lastFetchedUrls: List<String>, // 마지막 성공 수집에서 실제로 받은 페이지(admin-v1.md lastFetchedUrls)
)
