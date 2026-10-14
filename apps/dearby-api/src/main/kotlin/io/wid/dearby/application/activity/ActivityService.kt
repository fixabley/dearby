package io.wid.dearby.application.activity

import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ActivityService(
    private val activities: ActivityRepository,
    private val programs: ProgramRepository,
    private val organizations: OrganizationRepository,
) {

    @Transactional(readOnly = true)
    fun search(condition: ActivitySearch): ActivityPage {
        if (condition.size !in 1..MAX_PAGE_SIZE) throw InvalidInputException("size는 1에서 ${MAX_PAGE_SIZE} 사이여야 합니다")
        // 다음 페이지가 있는지 알기 위해 하나 더 읽는다
        val found = activities.search(condition.copy(size = condition.size + 1))
        val page = found.take(condition.size)
        val programById = programs.findAllById(page.map { it.programId }.toSet()).associateBy { it.id }
        val organizationById =
            organizations.findAllById(programById.values.map { it.organizationId }.toSet()).associateBy { it.id }
        // 프로그램·기관은 논리키라 없을 수 있다. 소속을 찾지 못한 활동은 목록에서 뺀다
        val items = page.mapNotNull { activity ->
            val program = programById[activity.programId] ?: return@mapNotNull null
            val organization = organizationById[program.organizationId] ?: return@mapNotNull null
            ActivityListing(activity, program, organization)
        }
        return ActivityPage(items, if (found.size > condition.size) page.last().id else null)
    }

    @Transactional(readOnly = true)
    fun getById(id: Id): ActivityListing {
        val notFound = { NotFoundException("활동을 찾을 수 없습니다", "Activity not found: $id") }
        val activity = activities.findById(id) ?: throw notFound()
        // 목록과 같이, 소속을 찾지 못한 활동은 없는 것으로 본다
        val program = programs.findAllById(listOf(activity.programId)).singleOrNull() ?: throw notFound()
        val organization = organizations.findAllById(listOf(program.organizationId)).singleOrNull() ?: throw notFound()
        return ActivityListing(activity, program, organization)
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 20
        const val MAX_PAGE_SIZE = 100
    }
}
