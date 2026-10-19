package io.wid.dearby.admin.application

// PATCH 프로그램 결과: 바뀐 프로그램과, 기본 종류를 일괄 적용했을 때 바뀐 활동 수(적용하지 않았으면 null)
data class ProgramUpdate(val listing: ProgramListing, val appliedActivityCount: Int?)
