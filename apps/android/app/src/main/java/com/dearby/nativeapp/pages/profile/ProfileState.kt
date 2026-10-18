package com.dearby.nativeapp.pages.profile

import com.dearby.nativeapp.entities.account.model.AccountHistory
import com.dearby.nativeapp.entities.account.model.AccountProfile
import com.dearby.nativeapp.entities.account.model.ContactRules
import com.dearby.nativeapp.entities.account.model.ActivityInfoItem
import com.dearby.nativeapp.entities.account.model.ActivityInfoRef
import com.dearby.nativeapp.entities.account.model.ProfileJob
import com.dearby.nativeapp.entities.account.model.EvidenceMeta
import com.dearby.nativeapp.entities.account.model.ProfileVersionDetail
import com.dearby.nativeapp.widgets.profile.profileFields.JobTag
import com.dearby.nativeapp.widgets.profile.profileFields.ProfileExtraContact
import com.dearby.nativeapp.widgets.profile.profileFields.historyPeriod
import java.time.LocalDate
import java.util.UUID

enum class ProfilePhase { SIGNED_OUT, LOADING, LOADED, FAILED }
/** 내 프로필 (contract "프로필과 버전 이력"): the loaded profile and the in-place edit form; [form] differing from [profile] shows 저장/원래대로. */
data class ProfileViewState(
    val phase: ProfilePhase = ProfilePhase.LOADING, val profile: AccountProfile? = null, val form: ProfileFormState? = null,
    val errors: ProfileErrors? = null, val saving: Boolean = false, val error: String? = null,
    /** 직무 tag picker search (setup funnel and editor). */
    val jobSearch: PickerSearchState = PickerSearchState(),
) {
    /** Never saved yet: the setup funnel replaces the editor and the tab bar hides. */
    val settingUp get() = phase == ProfilePhase.LOADED && profile?.version == 0
}
/** Messages shown once the user tries to save (editor) or presses 다음 (setup funnel). */
data class ProfileErrors(val name: String? = null, val phone: String? = null, val email: String? = null)
fun ProfileFormState.errors() = ProfileErrors(
    name = "이름을 입력해 주세요.".takeIf { name.isBlank() },
    phone = when { phone.isBlank() -> "전화번호를 입력해 주세요."; !ContactRules.validPhone(phone) -> "전화번호는 숫자 8~15자리로 입력해 주세요."; else -> null },
    email = when { email.isBlank() -> "이메일을 입력해 주세요."; !ContactRules.validEmail(email) -> "이메일 주소를 확인해 주세요."; else -> null },
)
/** First profile entry: accordion sections in this order; completed ones collapse to a row that reopens on tap. */
enum class SetupStep(val label: String) { NAME("이름"), JOBS("직무"), PHONE("전화번호"), EMAIL("이메일"), INTRODUCTION("소개") }

/** The in-place edit form: required phone and email, added contacts by kind, 직무 tags, histories from the step funnel, and the version for `If-Match`. */
data class ProfileFormState(
    val name: String, val jobs: List<ProfileJob>, val introduction: String, val phone: String, val email: String,
    val extras: List<ProfileExtraContact>, val histories: List<AccountHistory>, val version: Int, val phoneId: String, val emailId: String,
) {
    companion object {
        const val MAX_JOBS = 10
        fun from(profile: AccountProfile): ProfileFormState {
            val phone = profile.contacts.firstOrNull { it.kind == "phone" }
            val email = profile.contacts.firstOrNull { it.kind == "email" }
            return ProfileFormState(profile.name, profile.jobs, profile.introduction, phone?.value.orEmpty(), email?.value.orEmpty(),
                profile.contacts.filter { it.id != phone?.id && it.id != email?.id }.map { ProfileExtraContact(it.id, it.kind, it.value) },
                profile.histories, profile.version, phone?.id ?: UUID.randomUUID().toString(), email?.id ?: UUID.randomUUID().toString())
        }
    }
    /** Chips for the tag picker. */
    val jobTags get() = jobs.map { JobTag(it.id, it.title, pending = it.isCustom && it.approval == "pending", rejected = it.approval == "rejected") }
    /** A picked job (same job once) or a typed name (same name once); over [MAX_JOBS] nothing is added. */
    fun withJob(pickedId: Long?, pickedName: String?, typed: String = ""): ProfileFormState {
        val picked = if (pickedId != null && pickedName != null) ActivityInfoRef(pickedId, pickedName) else null
        val name = typed.trim()
        if (jobs.size >= MAX_JOBS) return this
        if (picked != null && jobs.any { it.job?.id == picked.id }) return this
        if (picked == null && (name.isEmpty() || jobs.any { it.job == null && it.jobName == name })) return this
        return copy(jobs = jobs + ProfileJob(UUID.randomUUID().toString(), picked, if (picked == null) name else null, if (picked == null) "pending" else "approved"))
    }
    fun withoutJob(id: String) = copy(jobs = jobs.filter { it.id != id })
    val jobsText get() = jobs.joinToString(" · ") { it.title }
    /** Puts a finished funnel draft in place (same id) or appends it. */
    fun put(history: AccountHistory) = copy(histories = if (histories.any { it.id == history.id }) histories.map { if (it.id == history.id) history else it } else histories + history)
}

/**
 * One history being entered step by step (2026-10-18 퍼널 분리). 조직 이력: 조직 → 역할 → 기간 → 설명 → 확인.
 * 활동 이력: 활동(전체 검색) → 역할 → 기간 → 설명 → 확인; 활동을 직접 적었을 때만 조직(필수)·프로그램(선택)을 활동 다음에 묻는다.
 * 활동 정보에서 고른 활동은 그 소속 조직·프로그램을 함께 채우고, 서버도 activityId로 소속을 맞춘다.
 */
data class HistoryDraft(
    val id: String = UUID.randomUUID().toString(), val kind: String = "organization",
    val organization: ActivityInfoRef? = null, val organizationName: String = "",
    val program: ActivityInfoRef? = null, val programName: String = "",
    val activity: ActivityInfoRef? = null, val activityName: String = "",
    val role: ActivityInfoRef? = null, val roleName: String = "",
    val start: LocalDate? = null, val end: LocalDate? = null, val ongoing: Boolean = true, val description: String = "",
    val approval: String = "approved", val approvalReason: String? = null, val verification: String = "unverified", val evidence: List<EvidenceMeta> = emptyList(),
) {
    enum class Step { KIND, ORGANIZATION, PROGRAM, ACTIVITY, ROLE, PERIOD, DESCRIPTION, SUMMARY }
    val isActivity get() = kind == "activity"
    /** A hand-typed activity (not from the catalogue) still needs its 조직. */
    val customActivity get() = isActivity && activity == null && activityName.isNotBlank()
    val steps get() = if (isActivity) listOfNotNull(Step.KIND, Step.ACTIVITY, Step.ORGANIZATION.takeIf { customActivity }, Step.PROGRAM.takeIf { customActivity }, Step.ROLE, Step.PERIOD, Step.DESCRIPTION, Step.SUMMARY)
        else listOf(Step.KIND, Step.ORGANIZATION, Step.ROLE, Step.PERIOD, Step.DESCRIPTION, Step.SUMMARY)
    fun error(step: Step): String? = when (step) {
        Step.ORGANIZATION -> "조직을 고르거나 적어 주세요.".takeIf { step in steps && organization == null && organizationName.isBlank() }
        Step.ACTIVITY -> "활동을 고르거나 적어 주세요.".takeIf { isActivity && activity == null && activityName.isBlank() }
        Step.ROLE -> "역할을 고르거나 적어 주세요.".takeIf { role == null && roleName.isBlank() }
        Step.PERIOD -> when { start == null -> "시작일을 골라 주세요."; !ongoing && end != null && end < start -> "종료일은 시작일 이후로 골라 주세요."; else -> null }
        else -> null
    }
    val history get() = AccountHistory(id, kind,
        organization, organizationName.trim().ifEmpty { null }.takeIf { organization == null },
        program, programName.trim().ifEmpty { null }.takeIf { program == null },
        if (isActivity) activity else null, activityName.trim().ifEmpty { null }.takeIf { isActivity && activity == null },
        role, roleName.trim().ifEmpty { null }.takeIf { role == null },
        start?.toString().orEmpty(), if (ongoing) null else end?.toString(), description.trim(), approval, approvalReason, verification, evidence)
    val summaryLine get() = (if (isActivity) "활동 참여" else "조직 활동") + " · " + historyPeriod(start, if (ongoing) null else end, ongoing)
    /** What the summary shows: 조직 · 프로그램 · 활동 and the role, from the draft itself. */
    val chainText get() = history.chain.joinToString(" · ")
    val roleText get() = history.roleTitle
    val hasCustomEntry get() = history.hasCustomEntry
    /** A picker choice for [step]; clearing a parent also clears what depended on it. A picked activity brings its 조직·프로그램 along. */
    fun picked(step: Step, id: Long, name: String, organization: ActivityInfoRef? = null, program: ActivityInfoRef? = null): HistoryDraft = when (step) {
        Step.ORGANIZATION -> copy(organization = ActivityInfoRef(id, name), organizationName = "", program = null, programName = "", activity = null, activityName = "")
        Step.PROGRAM -> copy(program = ActivityInfoRef(id, name), programName = "", activity = null, activityName = "")
        Step.ACTIVITY -> copy(activity = ActivityInfoRef(id, name), activityName = "",
            organization = organization ?: this.organization, organizationName = if (organization != null) "" else organizationName,
            program = program ?: this.program, programName = if (program != null) "" else programName)
        Step.ROLE -> copy(role = ActivityInfoRef(id, name), roleName = "")
        else -> this
    }
    fun cleared(step: Step): HistoryDraft = when (step) {
        Step.ORGANIZATION -> copy(organization = null); Step.PROGRAM -> copy(program = null); Step.ACTIVITY -> copy(activity = null); Step.ROLE -> copy(role = null); else -> this
    }
    fun typed(step: Step, name: String): HistoryDraft = when (step) {
        Step.ORGANIZATION -> copy(organizationName = name, organization = null); Step.PROGRAM -> copy(programName = name, program = null)
        Step.ACTIVITY -> copy(activityName = name, activity = null); Step.ROLE -> copy(roleName = name, role = null); else -> this
    }
    fun pickedName(step: Step): String? = when (step) { Step.ORGANIZATION -> organization?.name; Step.PROGRAM -> program?.name; Step.ACTIVITY -> activity?.name; Step.ROLE -> role?.name; else -> null }
    fun typedName(step: Step): String = when (step) { Step.ORGANIZATION -> organizationName; Step.PROGRAM -> programName; Step.ACTIVITY -> activityName; Step.ROLE -> roleName; else -> "" }

    companion object {
        fun from(h: AccountHistory) = HistoryDraft(h.id, h.kind,
            h.organization, if (h.organization == null) h.organizationName.orEmpty() else "",
            h.program, if (h.program == null) h.programName.orEmpty() else "",
            h.activity, if (h.activity == null) h.activityName.orEmpty() else "",
            h.role, if (h.role == null) h.roleName.orEmpty() else "",
            runCatching { LocalDate.parse(h.startDate.take(10)) }.getOrNull(), h.endDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() },
            h.endDate == null, h.description, h.approval, h.approvalReason, h.verification, h.evidence)
    }
}
/** One history as the profile list shows it; computed outside the composable so pages render State only. */
data class HistoryRowState(val id: String, val title: String, val detail: String, val verified: Boolean, val approvalTag: String?, val saved: Boolean)
fun AccountHistory.rowState(saved: Boolean) = HistoryRowState(id, chain.joinToString(" · "),
    listOf(roleTitle, periodText(startDate, endDate)).filter { it.isNotEmpty() }.joinToString(" · "), isVerified,
    if (hasCustomEntry || !isApproved) when (approval) { "approved" -> "승인됨"; "rejected" -> "거절됨"; else -> "승인 대기" } else null, saved)
/** `2026.03 – 2026.06`, 진행 중이면 `2026.03 – 진행 중`. */
fun periodText(start: String, end: String?): String {
    val month = { text: String -> text.take(7).replace('-', '.') }
    return "${month(start)} – ${end?.let(month) ?: "진행 중"}"
}
/** A saved version as the detail screen shows it (no entity types in the rendering code). */
data class VersionDetailView(val version: Int, val createdAt: String, val name: String, val job: String, val introduction: String,
    val contacts: List<Pair<String, String>>, val histories: List<HistoryRowState>)
fun ProfileVersionDetail.view() = VersionDetailView(version, createdAt, profile.name, profile.job, profile.introduction,
    profile.contacts.map { it.label to it.value }, profile.histories.map { it.rowState(true) })

/** The funnel sheet: which draft is open, whether the history exists on the server (evidence can be attached), and the picker search. */
data class FunnelState(val draft: HistoryDraft, val saved: Boolean, val step: HistoryDraft.Step, val tried: Boolean = false,
    val search: PickerSearchState = PickerSearchState(), val evidenceBusy: Boolean = false, val evidenceError: String? = null)
data class PickerSearchState(val query: String = "", val items: List<ActivityInfoItem> = emptyList(), val loading: Boolean = false, val failed: Boolean = false)

/** 변경 이력 screens. */
data class VersionsState(val items: List<Pair<Int, String>> = emptyList(), val nextCursor: Int? = null,
    val loading: Boolean = true, val error: String? = null, val detail: VersionDetailView? = null,
    val detailError: String? = null, val restoring: Boolean = false)
