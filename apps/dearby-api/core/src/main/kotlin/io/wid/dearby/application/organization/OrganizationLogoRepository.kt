package io.wid.dearby.application.organization

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.OrganizationLogo
import java.time.OffsetDateTime

// 조직 로고 저장소 포트(admin-v1.md "조직 로고"). 구현은 adaptor/persistence
interface OrganizationLogoRepository {

    fun find(organizationId: Id): OrganizationLogo?

    // 로고가 있는 조직만 돌려준다
    fun updatedAtByOrganization(organizationIds: Collection<Id>): Map<Id, OffsetDateTime>

    // 있으면 바꾼다
    fun save(logo: OrganizationLogo)

    // 없어도 그냥 끝난다
    fun delete(organizationId: Id)

    // 대표색(#RRGGBB)이 있는 것만(native-v1.md "맞춤 대기열" brandColor). 저장할 때 서버가 계산한다
    fun brandColors(ids: Collection<Id>): Map<Id, String>

    // 대표색이 비어 있는 로고를 다시 계산한다(밤 배치). 색을 찾은 수
    fun backfillBrandColors(): Int
}
