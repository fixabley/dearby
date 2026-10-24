package io.wid.dearby.application.activity

import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.application.program.ProgramSummaryRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.PublicationStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Service
class ActivityService(
    private val activities: ActivityRepository,
    private val programs: ProgramRepository,
    private val organizations: OrganizationRepository,
    private val summaries: ProgramSummaryRepository,
) {

    @Transactional(readOnly = true)
    fun search(condition: ActivitySearch): ActivityPage {
        if (condition.size !in 1..MAX_PAGE_SIZE) throw InvalidInputException("size는 1에서 ${MAX_PAGE_SIZE} 사이여야 합니다")
        // 다음 페이지가 있는지 알기 위해 하나 더 읽는다
        val found = activities.search(condition.copy(size = condition.size + 1))
        val raw = found.take(condition.size)
        // 2차 판정(native-v1.md "대상자 조건"): 1차(SQL)를 지난 활동의 조건 트리를 op대로 평가한다. 걸러진 만큼 이 페이지가 짧아질 수 있고 cursor는 그대로 이어진다
        val page = condition.eligibility?.let { filter -> raw.filter { it.eligibility?.accepts(filter) ?: true } } ?: raw
        val programById = programs.findAllById(page.map { it.programId }.toSet()).associateBy { it.id }
        val organizationById =
            organizations.findAllById(programById.values.map { it.organizationId }.toSet()).associateBy { it.id }
        // 프로그램·기관은 논리키라 없을 수 있다. 소속을 찾지 못한 활동은 목록에서 뺀다
        val items = page.mapNotNull { activity ->
            val program = programById[activity.programId] ?: return@mapNotNull null
            val organization = organizationById[program.organizationId] ?: return@mapNotNull null
            ActivityListing(activity, program, organization)
        }
        return ActivityPage(withProgramFields(items), if (found.size > condition.size) raw.last().id else null)
    }

    @Transactional(readOnly = true)
    fun getById(id: Id, publicationStatus: PublicationStatus? = PublicationStatus.PUBLISHED): ActivityListing {
        val notFound = { NotFoundException("활동을 찾을 수 없습니다", "Activity not found: $id") }
        val activity = activities.findById(id, publicationStatus) ?: throw notFound()
        // 목록과 같이, 소속을 찾지 못한 활동은 없는 것으로 본다
        val program = programs.findAllById(listOf(activity.programId)).singleOrNull() ?: throw notFound()
        val organization = organizations.findAllById(listOf(program.organizationId)).singleOrNull() ?: throw notFound()
        return withProgramFields(listOf(ActivityListing(activity, program, organization))).single()
    }

    // 분야 태그가 없는 활동은 프로그램의 분야 태그 순서를 쓴다(native-v1.md "분야·성격 태그와 아이콘")
    private fun withProgramFields(items: List<ActivityListing>): List<ActivityListing> {
        val programIds = items.filter { it.activity.fieldTags.isEmpty() }.map { it.program.id }.toSet()
        if (programIds.isEmpty()) return items
        val tags = summaries.tagsByProgram(programIds, OffsetDateTime.now(ZoneOffset.UTC))
        return items.map { item ->
            if (item.activity.fieldTags.isNotEmpty()) item
            else item.copy(activity = item.activity.copy(fieldTags = tags[item.program.id]?.fieldTags.orEmpty()))
        }
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
    }
}
