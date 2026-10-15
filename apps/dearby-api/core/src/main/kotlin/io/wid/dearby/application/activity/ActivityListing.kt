package io.wid.dearby.application.activity

import io.wid.dearby.domain.Activity
import io.wid.dearby.domain.Organization
import io.wid.dearby.domain.Program

// 목록 한 항목으로 화면을 그릴 수 있게 소속 프로그램·기관을 함께 담는다
data class ActivityListing(
    val activity: Activity,
    val program: Program,
    val organization: Organization,
)
