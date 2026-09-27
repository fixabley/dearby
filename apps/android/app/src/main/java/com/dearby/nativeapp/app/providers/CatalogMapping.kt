package com.dearby.nativeapp.app.providers

import com.dearby.nativeapp.entities.catalog.model.CatalogModel
import com.dearby.nativeapp.entities.catalog.model.CatalogLocalModel
import com.dearby.nativeapp.pages.catalog.ActivityState
import com.dearby.nativeapp.pages.catalog.SavedGroupState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun catalogDate(value: String?, zone: String? = null): String = value?.let { runCatching { DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm z", Locale.KOREAN).withZone(zone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()).format(Instant.parse(it)) }.getOrNull() } ?: "확인되지 않음"

fun CatalogModel.activityStates(local: CatalogLocalModel, now: Instant): List<ActivityState> = activities.map { activity ->
    fun known(value: String?) = value?.takeIf { it.isNotBlank() } ?: "확인되지 않음"
    val current = activity.current(now)
    ActivityState(activity.id, activity.programId, activity.organizationId, activity.title,
        programs.find { it.id == activity.programId }?.title ?: "프로그램 확인 필요",
        organizations.find { it.id == activity.organizationId }?.name ?: "조직 확인 필요", activity.summary,
        current, if (current) "모집 중" else when { activity.closed(now) -> "모집 종료"; activity.recruitmentStatus == "scheduled" && activity.fresh(now) -> "모집 예정"; else -> "현재 모집 여부 확인 필요" },
        when (activity.participationType) { "selection" -> "선발형 · 신청 후 선정 필요"; "registration" -> "참가등록형"; else -> "참가 방식 확인 필요" },
        known(activity.dateLabel), buildList {
            add("참가 대상" to known(activity.audience)); add("지원 조건" to known(activity.qualification))
            add("모집 직군" to activity.roles.joinToString(" · ").ifBlank { "확인되지 않음" })
            add("신청 시작" to catalogDate(activity.recruitmentStartAt)); add("신청 마감" to catalogDate(activity.recruitmentEndAt))
            add("비용" to known(activity.cost)); add("장소" to known(activity.location))
            activity.schedules.forEach {
                val time = when {
                    it.startAt == null && it.endAt == null -> "시간 미정"
                    it.endAt == null -> "${catalogDate(it.startAt, it.timeZone)} · 종료 시각 미정"
                    it.startAt == null -> "시작 시각 미정 · ${catalogDate(it.endAt, it.timeZone)} 종료"
                    else -> "${catalogDate(it.startAt, it.timeZone)} ~ ${catalogDate(it.endAt, it.timeZone)}"
                }
                add((if (it.title == activity.title) "행사 시간" else it.title) to (if (it.dateLabel == activity.dateLabel) time else "${known(it.dateLabel)}\n$time"))
            }
        }, activity.officialUrl, activity.applicationUrl, catalogDate(activity.sourceCheckedAt), activity.sourceNote,
        activity.programId in local.programs, activity.organizationId in local.organizations, local.reports[activity.id])
}
fun CatalogModel.savedGroups(local: CatalogLocalModel): List<SavedGroupState> =
    local.programs.map { SavedGroupState(it, programs.find { program -> program.id == it }?.title ?: "프로그램 정보 확인 필요", false) } +
        local.organizations.map { SavedGroupState(it, organizations.find { organization -> organization.id == it }?.name ?: "조직 정보 확인 필요", true) }
