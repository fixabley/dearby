package io.wid.dearby.application.program

import io.wid.dearby.application.organization.OrganizationRepository
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
        val page = found.take(condition.size)
        return ProgramSummaryPage(page, if (found.size > condition.size) condition.cursor + condition.size else null)
    }

    // 대상자 필터(native-v1.md "대상자 조건"): 열린 활동을 1차(SQL)·2차(JSON op) 판정으로 거른 뒤 그 활동들로 묶음을 만든다.
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
            ProgramSummary(
                id = program.id,
                name = program.name,
                nameEn = program.nameEn,
                organizationId = organization.id,
                organizationName = organization.name,
                organizationNameEn = organization.nameEn,
                openActivityCount = activities.size,
                nearestRecruitmentEndAt = activities.mapNotNull { it.recruitmentEndAt }.minOrNull(),
                matchedActivityIds = matchedIds,
            )
        }.sortedWith(compareBy<ProgramSummary, OffsetDateTime?>(nullsLast()) { it.nearestRecruitmentEndAt }.thenBy { it.name }.thenBy { it.id })
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
        const val MAX_QUERY_LENGTH = 100
    }
}
