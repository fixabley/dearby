package io.wid.dearby.admin.application

import io.wid.dearby.application.profile.RoleRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.profile.Role
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 역할 활동 정보 관리. 이름은 띄어쓰기를 뺀 값으로 유일(조직·프로그램과 같은 규칙)
@Service
class RoleAdminService(private val roles: RoleRepository) {

    @Transactional(readOnly = true)
    fun search(q: String?, cursor: Id?, size: Int): Page<Role> {
        if (size !in 1..100) throw InvalidInputException("size는 1에서 100 사이여야 합니다")
        val found = roles.search(q, cursor, size + 1)
        val page = found.take(size)
        return Page(page, if (found.size > size) page.last().id else null)
    }

    @Transactional
    fun create(name: String, nameEn: String?): Role {
        val trimmed = requireName(name)
        val english = nameEn?.trim()?.ifEmpty { null }
        requireUnique(trimmed, english, null)
        val id = unique { roles.create(trimmed, english) }
        return get(id)
    }

    // 보낸 값만 바꾼다. nameEn은 빈 문자열이면 지운다
    @Transactional
    fun update(id: Id, name: String?, nameEn: String?): Role {
        val current = get(id)
        val newName = name?.let(::requireName) ?: current.name
        val newNameEn = if (nameEn == null) current.nameEn else nameEn.trim().ifEmpty { null }
        requireUnique(newName, newNameEn, id)
        unique { roles.update(id, newName, newNameEn) }
        return get(id)
    }

    @Transactional
    fun delete(id: Id) {
        get(id)
        if (roles.inUse(id)) throw InvalidInputException("이력이 쓰고 있어 삭제할 수 없습니다")
        roles.delete(id)
    }

    private fun get(id: Id): Role = roles.findAllById(listOf(id)).singleOrNull() ?: throw NotFoundException("역할을 찾을 수 없습니다", "Role not found: $id")

    private fun requireUnique(name: String, nameEn: String?, exceptId: Id?) {
        if (roles.existsName(name, exceptId)) throw InvalidInputException("같은 이름의 역할이 있습니다")
        if (nameEn != null && roles.existsName(nameEn, exceptId, english = true)) throw InvalidInputException("같은 영문 이름의 역할이 있습니다")
    }

    private fun <T> unique(write: () -> T): T = try {
        write()
    } catch (e: DuplicateKeyException) {
        throw InvalidInputException("같은 이름의 역할이 있습니다", e.message ?: "duplicate role name")
    }

    private fun requireName(name: String): String {
        val trimmed = name.trim().ifEmpty { throw InvalidInputException("이름을 입력해 주세요") }
        if (trimmed.length > 100) throw InvalidInputException("이름은 100자까지입니다")
        return trimmed
    }
}
