package io.wid.dearby.admin.application

import io.wid.dearby.domain.Id
import io.wid.dearby.domain.Organization

// 조직도에서 펼칠 것이 있는지 보여 주는 하위 조직 수·프로그램 수를 함께 담는다
// ancestors: 최상위부터 바로 위 조직까지(id, 이름). 검색 결과에서 상위 이름을 보여 주고 경로를 펼칠 때 쓴다
data class OrganizationListing(
    val organization: Organization,
    val childCount: Int,
    val programCount: Int,
    val ancestors: List<Pair<Id, String>>,
)
