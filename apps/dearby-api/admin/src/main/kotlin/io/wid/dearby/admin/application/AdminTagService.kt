package io.wid.dearby.admin.application

import io.wid.dearby.application.tag.TagRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.Tag
import io.wid.dearby.domain.TagKind
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 태그 사전 관리(native-v1.md "태그"). 이름 변경은 연결을 유지하고, 삭제는 연결도 지운다(FK cascade)
@Service
class AdminTagService(private val tags: TagRepository) {

    @Transactional(readOnly = true)
    fun list(): List<Tag> = tags.findAll()

    // 같은 이름(띄어쓰기 무시)이 이미 있으면 그 태그를 그대로 돌려준다(종류·상위 분야도 바꾸지 않는다)
    @Transactional
    fun create(name: String?, nameEn: String?, kind: TagKind? = null, parentId: Id? = null): Tag {
        val finalKind = kind ?: TagKind.TRAIT
        requireParent(null, finalKind, parentId)
        return tags.create(requireName(name), nameEn?.trim()?.ifEmpty { null }, finalKind, parentId)
    }

    // kind는 생략하면 유지
    @Transactional
    fun update(id: Id, name: String?, nameEn: String?, kind: TagKind? = null, parent: ParentChange = ParentChange.Keep): Tag {
        val current = tags.findAllById(listOf(id)).singleOrNull() ?: throw NotFoundException("태그를 찾을 수 없습니다", "Tag not found: $id")
        val finalKind = kind ?: current.kind
        val finalParent = when (parent) {
            ParentChange.Keep -> current.parentId
            ParentChange.Clear -> null
            is ParentChange.Set -> parent.id
        }
        requireParent(id, finalKind, finalParent)
        tags.update(id, name?.let(::requireName), nameEn?.trim(), finalKind, finalParent)
        return tags.findAllById(listOf(id)).single()
    }

    sealed interface ParentChange {
        data object Keep : ParentChange
        data object Clear : ParentChange
        data class Set(val id: Id) : ParentChange
    }

    // 하위 분야(native-v1.md "분야·성격 태그와 아이콘"): 상위 분야가 있으면 그 태그도 상위 분야도 FIELD이고, 상위 분야는 최상위이며, 이 태그에는 하위 분야가 없다(한 단계).
    // 하위 분야가 있는 태그는 FIELD로 남아야 한다
    private fun requireParent(id: Id?, kind: TagKind, parentId: Id?) {
        val hasChildren = id != null && tags.hasChildren(id)
        if (kind != TagKind.FIELD && (parentId != null || hasChildren)) throw InvalidInputException("하위 분야는 분야 태그끼리만 둘 수 있습니다")
        if (parentId == null) return
        val parent = tags.findAllById(listOf(parentId)).singleOrNull() ?: throw InvalidInputException("없는 태그입니다: $parentId")
        if (parent.id == id || parent.kind != TagKind.FIELD || parent.parentId != null || hasChildren) {
            throw InvalidInputException("상위 분야는 하위 분야가 아닌 다른 분야 태그여야 합니다(한 단계)")
        }
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
