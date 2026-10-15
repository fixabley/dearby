package io.wid.dearby.admin.application

import io.wid.dearby.domain.Organization

// 조직도에서 펼칠 것이 있는지 보여 주는 하위 조직 수·프로그램 수를 함께 담는다
data class OrganizationListing(val organization: Organization, val childCount: Int, val programCount: Int)
