package io.wid.dearby.application.profile

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.profile.Role

// 역할 활동 정보 저장소. 구현은 adaptor/persistence
interface RoleRepository {

    fun findAllById(ids: Collection<Id>): List<Role>

    // q는 한글·영문 이름 부분 일치. id 오름차순으로 after 다음부터 최대 size개
    fun search(q: String?, after: Id?, size: Int): List<Role>

    fun create(name: String, nameEn: String?): Id

    fun update(id: Id, name: String, nameEn: String?)

    // 띄어쓰기를 뺀 이름이 같은 역할이 있는지(english면 영문 이름끼리). exceptId는 자기 자신
    fun existsName(name: String, exceptId: Id?, english: Boolean = false): Boolean

    fun inUse(id: Id): Boolean

    fun delete(id: Id): Boolean
}
