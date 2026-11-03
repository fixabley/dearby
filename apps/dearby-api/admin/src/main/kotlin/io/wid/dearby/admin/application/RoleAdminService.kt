package io.wid.dearby.admin.application

import io.wid.dearby.application.activity.ActivityRepository
import io.wid.dearby.application.organization.OrganizationRepository
import io.wid.dearby.application.profile.RoleRepository
import io.wid.dearby.application.program.ProgramRepository
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.InvalidInputException
import io.wid.dearby.domain.NotFoundException
import io.wid.dearby.domain.profile.Role
import io.wid.dearby.domain.profile.RoleOwnerKind
import io.wid.dearby.domain.profile.RoleSource
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

// 역할 관리(admin-v1.md "이력 검토·역할"). 역할은 조직·프로그램·활동 중 하나에 귀속되고 이름은 그 단위 안에서 띄어쓰기를 뺀 값으로 유일
@Service
class RoleAdminService(
    private val roles: RoleRepository,
    private val organizations: OrganizationRepository,
    private val programs: ProgramRepository,
    private val activities: ActivityRepository,
) {

    @Transactional(readOnly = true)
    fun search(kind: RoleOwnerKind, ownerId: Id, q: String?, cursor: Id?, size: Int): Page<Role> {
        if (size !in 1..100) throw InvalidInputException("size는 1에서 100 사이여야 합니다")
        val found = roles.search(kind, ownerId, q, cursor, size + 1)
        val page = found.take(size)
        return Page(page, if (found.size > size) page.last().id else null)
    }

    @Transactional
    fun create(kind: RoleOwnerKind, ownerId: Id, name: String, nameEn: String?): Role {
        requireOwner(kind, ownerId)
        val trimmed = requireName(name)
        val english = nameEn?.trim()?.ifEmpty { null }
        requireUnique(kind, ownerId, trimmed, english, null)
        val id = unique { roles.create(kind, ownerId, trimmed, english, RoleSource.CURATED) }
        return get(id)
    }

    // 보낸 값만 바꾼다. nameEn은 빈 문자열이면 지운다. 귀속은 바꾸지 않는다
    @Transactional
    fun update(id: Id, name: String?, nameEn: String?, position: Int?): Role {
        val current = get(id)
        val newName = name?.let(::requireName) ?: current.name
        val newNameEn = if (nameEn == null) current.nameEn else nameEn.trim().ifEmpty { null }
        if (position != null && position < 0) throw InvalidInputException("position은 0 이상이어야 합니다")
        requireUnique(current.owner.kind, current.owner.id!!, newName, newNameEn, id)
        unique { roles.update(id, newName, newNameEn, position ?: current.position) }
        return get(id)
    }

    @Transactional
    fun delete(id: Id) {
        get(id)
        if (roles.inUse(id)) throw InvalidInputException("이력이 쓰고 있어 삭제할 수 없습니다")
        roles.delete(id)
    }

    private fun get(id: Id): Role = roles.findAllById(listOf(id)).singleOrNull() ?: throw NotFoundException("역할을 찾을 수 없습니다", "Role not found: $id")

    // 귀속 단위는 등록된 것이어야 한다. 활동은 초안도 된다(운영자가 게시 전에 역할을 고친다)
    private fun requireOwner(kind: RoleOwnerKind, ownerId: Id) {
        val found = when (kind) {
            RoleOwnerKind.ORGANIZATION -> organizations.findAllById(listOf(ownerId)).isNotEmpty()
            RoleOwnerKind.PROGRAM -> programs.findAllById(listOf(ownerId)).isNotEmpty()
            RoleOwnerKind.ACTIVITY -> activities.findById(ownerId, null) != null
        }
        if (!found) throw InvalidInputException("${LABELS.getValue(kind)}을(를) 찾을 수 없습니다", "${kind.name} not found: $ownerId")
    }

    private fun requireUnique(kind: RoleOwnerKind, ownerId: Id, name: String, nameEn: String?, exceptId: Id?) {
        if (roles.existsName(kind, ownerId, name, exceptId)) throw InvalidInputException("같은 이름의 역할이 있습니다")
        if (nameEn != null && roles.existsName(kind, ownerId, nameEn, exceptId, english = true)) throw InvalidInputException("같은 영문 이름의 역할이 있습니다")
    }

    private fun <T> unique(write: () -> T): T = try {
        write()
    } catch (e: DuplicateKeyException) {
        throw InvalidInputException("같은 이름의 역할이 있습니다", e.message ?: "duplicate role name")
    }

    private fun requireName(name: String): String {
        val trimmed = name.trim().ifEmpty { throw InvalidInputException("이름을 입력해 주세요") }
        if (trimmed.length > 200) throw InvalidInputException("이름은 200자까지입니다")
        return trimmed
    }

    private companion object {
        val LABELS = mapOf(RoleOwnerKind.ORGANIZATION to "조직", RoleOwnerKind.PROGRAM to "프로그램", RoleOwnerKind.ACTIVITY to "활동")
    }
}
