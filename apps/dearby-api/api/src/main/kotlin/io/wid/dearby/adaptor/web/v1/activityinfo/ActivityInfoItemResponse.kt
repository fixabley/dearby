package io.wid.dearby.adaptor.web.v1.activityinfo

// 앱 선택 창용 활동 정보 항목. organization은 프로그램에만, parentOrganizationId는 조직에만 있다
data class ActivityInfoItemResponse(
    val id: Long,
    val name: String,
    val nameEn: String?,
    val parentOrganizationId: Long? = null,
    val organization: ActivityInfoParentResponse? = null,
)
