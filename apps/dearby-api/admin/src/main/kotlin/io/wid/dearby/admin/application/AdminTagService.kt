package io.wid.dearby.admin.application

import io.wid.dearby.application.tag.TagRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.Tag
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 태그 사전 관리(native-v1.md "태그"). 이름 변경은 연결을 유지하고, 삭제는 연결도 지운다(FK cascade)
@Service
class AdminTagService(private val tags: TagRepository) {

    @Transactional(readOnly = true)
    fun list(): List<Tag> = tags.findAll()

    @Transactional
    fun create(name: String?, nameEn: String?): Tag = tags.create(requireName(name), nameEn?.trim()?.ifEmpty { null })

    @Transactional
    fun update(id: Id, name: String?, nameEn: String?): Tag {
        val trimmed = name?.let(::requireName)
        if (!tags.update(id, trimmed, nameEn?.trim())) throw NotFoundException("태그를 찾을 수 없습니다", "Tag not found: $id")
        return tags.findAllById(listOf(id)).single()
    }

    @Transactional
    fun delete(id: Id) {
        if (!tags.delete(id)) throw NotFoundException("태그를 찾을 수 없습니다", "Tag not found: $id")
    }

    // 보낸 id가 모두 사전에 있는지. 없는 id가 있으면 400
    @Transactional(readOnly = true)
    fun requireAll(ids: Collection<Id>): List<Tag> {
        val found = tags.findAllById(ids)
        val missing = ids.toSet() - found.map { it.id }.toSet()
        if (missing.isNotEmpty()) throw InvalidInputException("없는 태그입니다: ${missing.joinToString(", ")}")
        return found
    }

    private fun requireName(name: String?): String {
        val trimmed = name?.trim().orEmpty()
        if (trimmed.isEmpty()) throw InvalidInputException("태그 이름을 입력해 주세요")
        if (trimmed.length > MAX_NAME) throw InvalidInputException("태그 이름은 ${MAX_NAME}자까지입니다")
        return trimmed
    }

    private companion object {
        const val MAX_NAME = 50
    }
}
