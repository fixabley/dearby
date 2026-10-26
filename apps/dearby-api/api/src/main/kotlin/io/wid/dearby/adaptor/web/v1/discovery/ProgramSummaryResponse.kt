package io.wid.dearby.adaptor.web.v1.discovery

import io.wid.dearby.adaptor.web.ReferenceResponse
import io.wid.dearby.adaptor.web.v1.activities.ResponseLanguage
import io.wid.dearby.adaptor.web.v1.tags.TagResponse
import io.wid.dearby.application.program.ProgramSummary
import io.wid.dearby.domain.favorite.FavoriteIds
import java.time.OffsetDateTime

// 발견 화면의 프로그램 묶음 머리글(native-v1.md "발견 프로그램 묶음"). 이름은 요청 언어 값 하나(영문이 없으면 한글)
data class ProgramSummaryResponse(
    val id: Long,
    val name: String,
    val nameEn: String?,
    val organization: ReferenceResponse,
    val openActivityCount: Int,
    val nearestRecruitmentEndAt: OffsetDateTime?,
    val nearestIsActivityStart: Boolean, // nearestRecruitmentEndAt이 모집 마감이 아니라 활동 시작(모집 마감이 없는 활동)인지
    val matchedActivityIds: List<Long>,
    val upcomingActivityCount: Int, // 모집 예정 활동 수(native-v1.md "마감 숨기기")
    val nearestRecruitmentStartAt: OffsetDateTime?, // 모집 예정 활동의 가장 이른 모집 시작, 없으면 null
    val category: String?, // 프로그램 기본 종류 코드(native-v1.md "활동 종류"), 없으면 null
    val latestActivityAt: OffsetDateTime?, // 게시 활동의 가장 최근 활동 시작(native-v1.md "모집 중인 프로그램만 보기")
    val tags: List<TagResponse>, // 프로그램 태그, 없으면 게시 활동 태그의 합집합(최대 6개)(native-v1.md "태그"). 이전 앱 호환
    val fieldTags: List<TagResponse>, // 분야 태그 최대 2개, 서버 순서(native-v1.md "분야·성격 태그와 아이콘")
    val traitTags: List<TagResponse>, // 성격 태그 전부, 사전 순
    val brandColor: String?, // 로고(계승 포함) 대표색 "#RRGGBB", 커버가 없을 때 그라데이션. 없으면 null
    val coverUrl: String?, // 대기열 커버 경로 `/v1/programs/{id}/cover?v=…`(native-v1.md "맞춤 대기열"), 없거나 꺼졌으면 null
    val logoUrl: String?, // 프로그램 로고 경로 `/v1/programs/{id}/logo?v=…`(native-v1.md "프로그램 로고"), 없으면 null
    val hasTimetable: Boolean, // 모집 중·모집 예정 활동(필터 반영) 중 하나라도 시간표가 있으면 true(접힌 카드 "시간표" 배지, native-v1.md "트랙·세션 시간표")
    val favorited: Boolean, // 로그인 사용자가 즐겨찾기했는지(native-v1.md "즐겨찾기(하트)"). 비로그인은 false
) {
    companion object {
        fun from(summary: ProgramSummary, language: ResponseLanguage, favorites: FavoriteIds = FavoriteIds.NONE) = ProgramSummaryResponse(
            id = summary.id,
            name = language.pick(summary.name, summary.nameEn),
            nameEn = summary.nameEn,
            organization = ReferenceResponse(summary.organizationId, language.pick(summary.organizationName, summary.organizationNameEn)),
            openActivityCount = summary.openActivityCount,
            nearestRecruitmentEndAt = summary.nearestRecruitmentEndAt,
            nearestIsActivityStart = summary.nearestIsActivityStart,
            matchedActivityIds = summary.matchedActivityIds,
            upcomingActivityCount = summary.upcomingActivityCount,
            nearestRecruitmentStartAt = summary.nearestRecruitmentStartAt,
            category = summary.category?.name,
            latestActivityAt = summary.latestActivityAt,
            tags = summary.tags.map(TagResponse::from),
            fieldTags = summary.fieldTags.map(TagResponse::from),
            traitTags = summary.traitTags.map(TagResponse::from),
            brandColor = summary.brandColor,
            coverUrl = summary.coverUrl,
            logoUrl = summary.logoUrl,
            hasTimetable = summary.hasTimetable,
            favorited = summary.id in favorites.programs,
        )
    }
}

// nextCursor를 다음 요청의 cursor로 보낸다. null이면 마지막 페이지
data class ProgramSummaryPageResponse(val items: List<ProgramSummaryResponse>, val nextCursor: Int?)
