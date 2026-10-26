package io.wid.dearby.application.program

import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.domain.ActivityStatus
import io.wid.dearby.domain.InvalidInputException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

// 발견 화면의 프로그램 묶음 조회(native-v1.md "발견 프로그램 묶음")
@Service
class ProgramSummaryService(
    private val summaries: ProgramSummaryRepository,
    private val programs: ProgramRepository,
    private val organizations: OrganizationRepository,
    private val logos: EffectiveLogos,
    private val covers: ProgramCoverRepository,
) {

    @Transactional(readOnly = true)
    fun search(condition: ProgramSummarySearch): ProgramSummaryPage {
        if (condition.size !in 1..MAX_PAGE_SIZE) throw InvalidInputException("size는 1에서 ${MAX_PAGE_SIZE} 사이여야 합니다")
        if (condition.cursor < 0) throw InvalidInputException("cursor가 올바르지 않습니다")
        if ((condition.q?.length ?: 0) > MAX_QUERY_LENGTH) throw InvalidInputException("검색어는 ${MAX_QUERY_LENGTH}자까지입니다")
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        // 다음 페이지가 있는지 알기 위해 하나 더 읽는다
        val found = if (condition.eligibility == null) {
            summaries.search(condition.copy(size = condition.size + 1), now)
        } else {
            filteredSummaries(condition, now).drop(condition.cursor).take(condition.size + 1)
        }
        return ProgramSummaryPage(decorate(found.take(condition.size)), if (found.size > condition.size) condition.cursor + condition.size else null)
    }

    // 즐겨찾기한 프로그램의 묶음 머리(게시 활동이 하나도 없는 프로그램은 빠진다)
    @Transactional(readOnly = true)
    fun byIds(ids: Collection<io.wid.dearby.domain.Id>): Map<io.wid.dearby.domain.Id, ProgramSummary> {
        if (ids.isEmpty()) return emptyMap()
        val found = summaries.search(ProgramSummarySearch(null, null, 0, ids.size, programIds = ids.toSet()), OffsetDateTime.now(ZoneOffset.UTC))
        return decorate(found).associateBy { it.id }
    }

    // 묶음 머리의 태그(native-v1.md "태그", "분야·성격 태그와 아이콘")와 로고(native-v1.md "조직 로고와 로고 계승": 프로그램 로고, 없으면 가장 가까운 조직 로고).
    // 대상자 필터 경로와 추천도 거치도록 여기서 한 번에 찾는다
    @Transactional(readOnly = true)
    fun decorate(page: List<ProgramSummary>): List<ProgramSummary> {
        val tags = summaries.tagsByProgram(page.map { it.id }, OffsetDateTime.now(ZoneOffset.UTC))
        val logo = logos.of(page.associate { it.id to it.organizationId })
        val cover = covers.infoByProgram(page.map { it.id }).filterValues { it.enabled }
        return page.map { summary ->
            val found = tags[summary.id]
            summary.copy(
                tags = found?.tags.orEmpty(), fieldTags = found?.fieldTags.orEmpty().take(MAX_FIELD_TAGS), traitTags = found?.traitTags.orEmpty(),
                logoUrl = logo[summary.id]?.url, brandColor = logo[summary.id]?.brandColor, coverUrl = cover[summary.id]?.url,
            )
        }
    }

    // 대상자 필터(native-v1.md "대상자 조건"): 열린 활동(status가 있으면 그 상태의 활동)을 1차(SQL)·2차(JSON op) 판정으로 거른 뒤 그 활동들로 묶음을 만든다.
    // 열린 활동 수·가장 가까운 마감·검색어 일치도 모두 걸러진 활동 기준이다
    private fun filteredSummaries(condition: ProgramSummarySearch, now: OffsetDateTime): List<ProgramSummary> {
        val filter = condition.eligibility!!
        val q = condition.q?.trim()?.takeIf { it.isNotBlank() }
        val open = summaries.openActivities(condition, now).filter { it.eligibility?.accepts(filter) ?: true }
        val byProgram = open.groupBy { it.programId }
        val programById = programs.findAllById(byProgram.keys).associateBy { it.id }
        val organizationById = organizations.findAllById(programById.values.map { it.organizationId }.toSet()).associateBy { it.id }
        fun matches(vararg texts: String?) = q != null && texts.any { it?.contains(q, ignoreCase = true) == true }
        return byProgram.mapNotNull { (programId, activities) ->
            val program = programById[programId] ?: return@mapNotNull null
            val organization = organizationById[program.organizationId] ?: return@mapNotNull null
            val matchedIds = if (q == null) emptyList() else activities.filter { matches(it.name, it.nameEn) }.map { it.id }.sorted()
            if (q != null && matchedIds.isEmpty() && !matches(program.name, program.nameEn, organization.name, organization.nameEn)) return@mapNotNull null
            val recruiting = activities.filter { it.status == ActivityStatus.RECRUITING }
            val upcoming = activities.filter { it.status == ActivityStatus.RECRUITING_UPCOMING }
            ProgramSummary(
                id = program.id,
                name = program.name,
                nameEn = program.nameEn,
                organizationId = organization.id,
                organizationName = organization.name,
                organizationNameEn = organization.nameEn,
                openActivityCount = recruiting.size,
                nearestRecruitmentEndAt = recruiting.mapNotNull { it.recruitmentEndAt }.minOrNull(),
                upcomingActivityCount = upcoming.size,
                nearestRecruitmentStartAt = upcoming.mapNotNull { it.recruitmentStartAt }.minOrNull(),
                statusCounts = activities.groupingBy { it.status }.eachCount(),
                matchedActivityIds = matchedIds,
                category = program.category,
                hasTimetable = activities.any { it.hasTimetable },
            )
        }.sortedWith(compareBy<ProgramSummary, OffsetDateTime?>(nullsLast()) { summary -> byProgram.getValue(summary.id).filter { it.status == ActivityStatus.RECRUITING }.mapNotNull { it.sortKey }.minOrNull() }.thenBy { it.name }.thenBy { it.id })
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
        const val MAX_QUERY_LENGTH = 100
        const val MAX_FIELD_TAGS = 2 // 발견 카드 로고 밑 분야 아이콘 수(native-v1.md "분야·성격 태그와 아이콘")
    }
}
