package io.wid.dearby.application.profile

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.profile.Role
import io.wid.dearby.domain.profile.RoleOwnerKind
import io.wid.dearby.domain.profile.RoleSource

// 역할 저장소(native-v1.md "이력 (History) — 역할 매칭"). 역할은 늘 한 단위(kind, ownerId)에 속한다. 구현은 adaptor/persistence
interface RoleRepository {

    fun findAllById(ids: Collection<Id>): List<Role>

    // 한 단위의 역할. q는 한글·영문 이름 부분 일치. position·id 순으로 after(이전 페이지 마지막 id) 다음부터 최대 size개
    fun search(kind: RoleOwnerKind, ownerId: Id, q: String?, after: Id?, size: Int): List<Role>

    // 그 단위의 마지막 순서로 만든다
    fun create(kind: RoleOwnerKind, ownerId: Id, name: String, nameEn: String?, source: RoleSource): Id

    // 운영자가 고친 것이므로 source는 CURATED가 된다
    fun update(id: Id, name: String, nameEn: String?, position: Int)

    // 같은 단위 안에 띄어쓰기를 뺀 이름이 같은 역할이 있는지(english면 영문 이름끼리). exceptId는 자기 자신
    fun existsName(kind: RoleOwnerKind, ownerId: Id, name: String, exceptId: Id?, english: Boolean = false): Boolean

    fun inUse(id: Id): Boolean

    fun delete(id: Id): Boolean
}
