package io.wid.dearby.admin.application

import io.wid.dearby.application.activity.ActivityCurationRepository
import io.wid.dearby.application.activity.ActivityService
import io.wid.dearby.application.collection.WatchlistRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.ConflictException
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.collection.WatchEntry
import io.wid.dearby.domain.collection.WatchKind
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 조심 목록(admin-v1.md "조심 목록"): 운영자가 중복·오분류로 지정한 이름을 수집기가 피하게 한다
@Service
class WatchlistAdminService(
    private val watchlist: WatchlistRepository,
    private val programs: ProgramRepository,
    private val activities: ActivityService,
    private val curations: ActivityCurationRepository,
) {

    @Transactional(readOnly = true)
    fun list(programId: Id): List<WatchEntry> {
        requireProgram(programId)
        return watchlist.findAllByProgramId(programId)
    }

    @Transactional
    fun create(programId: Id, name: String, kind: WatchKind, canonicalActivityId: Id?, reason: String?): WatchEntry {
        requireProgram(programId)
        return add(programId, name, kind, canonicalActivityId, reason, exceptActivityId = null)
    }

    @Transactional
    fun delete(entryId: Id) {
        if (!watchlist.delete(entryId)) throw NotFoundException("조심 목록 항목을 찾을 수 없습니다", "Watch entry not found: $entryId")
    }

    // 중복으로 지정: 그 활동의 이름을 조심 목록에 올리고 활동을 지운다(수집 출처는 dismissed로 남아 같은 회차는 다시 생기지 않는다)
    @Transactional
    fun markDuplicate(activityId: Id, kind: WatchKind, canonicalActivityId: Id?, reason: String?) {
        val activity = activities.getById(activityId, publicationStatus = null).activity
        add(activity.programId, activity.name, kind, canonicalActivityId, reason, exceptActivityId = activityId)
        curations.delete(activityId)
    }

    private fun add(programId: Id, name: String, kind: WatchKind, canonicalActivityId: Id?, reason: String?, exceptActivityId: Id?): WatchEntry {
        val trimmed = name.trim().ifEmpty { throw InvalidInputException("이름을 입력해 주세요") }
        if (trimmed.length > MAX_NAME) throw InvalidInputException("이름은 ${MAX_NAME}자까지입니다")
        val note = reason?.trim()?.ifEmpty { null }?.also { if (it.length > MAX_REASON) throw InvalidInputException("사유는 ${MAX_REASON}자까지입니다") }
        val canonical = when (kind) {
            WatchKind.EXCLUDE -> null
            WatchKind.ALIAS -> {
                val id = canonicalActivityId ?: throw InvalidInputException("같은 활동으로 지정할 때는 정본 활동을 골라 주세요")
                if (id == exceptActivityId) throw InvalidInputException("정본은 지우려는 활동과 달라야 합니다")
                val target = runCatching { activities.getById(id, publicationStatus = null).activity }.getOrNull()
                    ?: throw InvalidInputException("정본 활동을 찾을 수 없습니다", "Canonical activity not found: $id")
                if (target.programId != programId) throw InvalidInputException("정본은 같은 프로그램의 활동이어야 합니다")
                id
            }
        }
        return try {
            watchlist.create(programId, trimmed, kind, canonical, note)
        } catch (e: DuplicateKeyException) {
            throw ConflictException("이 프로그램의 조심 목록에 같은 이름이 있습니다", e.message ?: "duplicate watch entry")
        }
    }

    private fun requireProgram(programId: Id) {
        if (programs.findAllById(listOf(programId)).isEmpty()) throw NotFoundException("프로그램을 찾을 수 없습니다", "Program not found: $programId")
    }

    private companion object {
        const val MAX_NAME = 200
        const val MAX_REASON = 500
    }
}
