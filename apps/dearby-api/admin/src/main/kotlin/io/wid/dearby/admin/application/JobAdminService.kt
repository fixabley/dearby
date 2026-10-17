package io.wid.dearby.admin.application

import io.wid.dearby.application.profile.JobRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.profile.Job
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 직무 활동 정보 관리(admin-v1.md "직무"). 조직과 같은 계층·이름 중복 규칙
@Service
class JobAdminService(private val jobs: JobRepository) {

    @Transactional(readOnly = true)
    fun search(q: String?, parentId: Id?, rootsOnly: Boolean, cursor: Id?, size: Int): Page<JobListing> {
        if (size !in 1..100) throw InvalidInputException("size는 1에서 100 사이여야 합니다")
        val found = jobs.search(q, parentId, rootsOnly, cursor, size + 1)
        val page = found.take(size)
        return Page(listings(page), if (found.size > size) page.last().id else null)
    }

    @Transactional
    fun create(name: String, nameEn: String?, parentId: Id?): JobListing {
        val trimmed = requireName(name)
        val english = nameEn?.trim()?.ifEmpty { null }
        parentId?.let(::require)
        requireUnique(parentId, trimmed, english, null)
        val id = unique { jobs.create(trimmed, english, parentId) }
        return listing(id)
    }

    // 보낸 값만 바꾼다(nameEn은 빈 문자열이면 지운다). 자기 자신이나 자기 하위 직무 아래로 옮기면 순환이라 거부한다
    @Transactional
    fun update(id: Id, name: String?, nameEn: String?, parentId: Id?, clearParent: Boolean): JobListing {
        val current = require(id)
        val parent = if (clearParent) null else parentId ?: current.parentId
        if (parent != null && parent != current.parentId) {
            require(parent)
            if (parent == id || id in jobs.ancestorIds(parent)) throw InvalidInputException("자기 자신이나 하위 직무 아래로 옮길 수 없습니다", "Cycle: $id under $parent")
        }
        val newName = name?.let(::requireName) ?: current.name
        val newNameEn = if (nameEn == null) current.nameEn else nameEn.trim().ifEmpty { null }
        if (newName != current.name || newNameEn != current.nameEn || parent != current.parentId) requireUnique(parent, newName, newNameEn, id)
        unique { jobs.update(id, newName, newNameEn, parent) }
        return listing(id)
    }

    // 하위 직무가 있거나 프로필이 쓰고 있으면 지우지 않는다
    @Transactional
    fun delete(id: Id) {
        require(id)
        if ((jobs.childCounts(listOf(id))[id] ?: 0) > 0) throw InvalidInputException("하위 직무가 있어 삭제할 수 없습니다. 먼저 옮기거나 지워 주세요")
        if (jobs.inUse(id)) throw InvalidInputException("프로필이 쓰고 있어 삭제할 수 없습니다")
        jobs.delete(id)
    }

    private fun require(id: Id): Job = jobs.findAllById(listOf(id)).singleOrNull() ?: throw NotFoundException("직무를 찾을 수 없습니다", "Job not found: $id")

    private fun listing(id: Id): JobListing = listings(listOf(require(id))).single()

    private fun listings(page: List<Job>): List<JobListing> {
        val ids = page.map { it.id }
        val counts = jobs.childCounts(ids)
        val ancestors = jobs.ancestors(ids)
        return page.map { JobListing(it, counts[it.id] ?: 0, ancestors[it.id].orEmpty()) }
    }

    private fun requireUnique(parentId: Id?, name: String, nameEn: String?, exceptId: Id?) {
        if (jobs.existsSiblingName(parentId, name, exceptId)) throw InvalidInputException("같은 계위에 같은 이름의 직무가 있습니다")
        if (nameEn != null && jobs.existsSiblingName(parentId, nameEn, exceptId, english = true)) throw InvalidInputException("같은 계위에 같은 영문 이름의 직무가 있습니다")
    }

    private fun <T> unique(write: () -> T): T = try {
        write()
    } catch (e: DuplicateKeyException) {
        throw InvalidInputException("같은 계위에 같은 이름의 직무가 있습니다", e.message ?: "duplicate job name")
    }

    private fun requireName(name: String): String {
        val trimmed = name.trim().ifEmpty { throw InvalidInputException("이름을 입력해 주세요") }
        if (trimmed.length > 100) throw InvalidInputException("이름은 100자까지입니다")
        return trimmed
    }
}
