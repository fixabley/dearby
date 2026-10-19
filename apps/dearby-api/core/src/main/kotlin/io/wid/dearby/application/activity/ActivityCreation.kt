package io.wid.dearby.application.activity

import io.wid.dearby.domain.ActivityCategory
import io.wid.dearby.domain.Id
import io.wid.dearby.domain.ParticipationType

// 운영자가 직접 만드는 활동(admin-v1.md "POST /v1/admin/activities", 2026-10-19). 초안·출처 없음·공식 출처·"운영자 등록".
// 날짜 범위는 insert 뒤 updateSchedule로 넣고, 여기서는 활동 기간의 원문 문구만 받는다
data class ActivityCreation(
    val programId: Id,
    val name: String,
    val nameEn: String?,
    val description: String,
    val descriptionEn: String?,
    val officialUrl: String,
    val applicationUrl: String?,
    val participationType: ParticipationType,
    val category: ActivityCategory?,
    val audience: String?,
    val location: String?,
    val cost: String?,
    val dateLabel: String,
    val curatedFields: List<String>, // 보낸 필드 이름(수집기가 덮어쓰지 않는다)
)
