package com.dearby.nativeapp.pages.profile

import com.dearby.nativeapp.entities.account.model.AccountHistory
import com.dearby.nativeapp.entities.account.model.AccountProfile
import com.dearby.nativeapp.entities.account.model.ContactRules
import com.dearby.nativeapp.entities.account.model.ActivityInfoItem
import com.dearby.nativeapp.entities.account.model.ActivityInfoRef
import com.dearby.nativeapp.entities.account.model.ProfileJob
import com.dearby.nativeapp.entities.account.model.EvidenceMeta
import com.dearby.nativeapp.entities.account.model.HistoryOwner
import com.dearby.nativeapp.entities.account.model.ProfileVersionDetail
import com.dearby.nativeapp.entities.account.model.currentOrganization
import com.dearby.nativeapp.widgets.card.cardContent.CardHistoryState
import com.dearby.nativeapp.widgets.card.cardContent.CardState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState
import com.dearby.nativeapp.widgets.profile.profileFields.EmailCodeEntry
import com.dearby.nativeapp.widgets.profile.profileFields.JobTag
import com.dearby.nativeapp.widgets.profile.profileFields.ProfileExtraContact
import com.dearby.nativeapp.widgets.profile.profileFields.historyPeriod
import java.time.LocalDate
import java.util.UUID

enum class ProfilePhase { SIGNED_OUT, LOADING, LOADED, FAILED }
/**
 * 내 프로필 (contract "프로필과 버전 이력"·"프로필 읽기/수정 모드", 2026-10-20): the loaded profile and the edit form. [editing] false shows the read-only rows;
 * the top-bar `수정` turns it on and a successful save (or 버리기) turns it off. Memory only.
 */
data class ProfileViewState(
    val phase: ProfilePhase = ProfilePhase.LOADING, val profile: AccountProfile? = null, val form: ProfileFormState? = null, val editing: Boolean = false,
    val errors: ProfileErrors? = null, val saving: Boolean = false, val error: String? = null,
    /** 개인정보 동의 (contract "개인정보 동의"): the consent screen replaces the profile until the server records the agreement. */
    val consentRequired: Boolean = false, val consenting: Boolean = false, val consentError: String? = null,
    /** 직무 tag picker search (setup funnel and editor). */
    val jobSearch: PickerSearchState = PickerSearchState(),
    /** 프로필 사진 (contract "프로필 사진"): its own upload/delete, saved at once and not part of 저장. */
    val photoBusy: Boolean = false, val photoError: String? = null,
    /** 연락처 인증 (contract "연락처 인증"): GitHub 연결·이메일 인증번호 in the editor. */
    val verify: ContactVerifyState = ContactVerifyState(),
    /** A one-line notice for the shell's Snackbar (연락처 인증 failures, "지금은 쓸 수 없어요"); the route shows it once and clears it. */
    val notice: String? = null,
    /** Counts GitHub 연결 returns, so the app brings the profile editor back in front (after process death the tab was lost). */
    val returned: Int = 0,
) {
    /** Never saved yet: the setup funnel replaces the editor and the tab bar hides. */
    val settingUp get() = phase == ProfilePhase.LOADED && profile?.version == 0
}
/**
 * 연락처 인증 in the editor (contract "연락처 인증", 2026-10-26). [githubAvailable]·[emailAvailable] turn false on a 503 (the server has no GitHub app or mail), which hides the
 * buttons; [challengeId] is the open 이메일 인증번호 challenge whose code the [entry] holds (null: the `인증번호 받기` button shows instead).
 */
data class ContactVerifyState(val githubAvailable: Boolean = true, val githubBusy: Boolean = false, val emailAvailable: Boolean = true, val emailBusy: Boolean = false,
                              val challengeId: String? = null, val entry: EmailCodeEntry = EmailCodeEntry())
/** Messages shown once the user tries to save (editor) or presses 다음 (setup funnel). */
data class ProfileErrors(val name: String? = null, val phone: String? = null, val email: String? = null)
fun ProfileFormState.errors() = ProfileErrors(
    name = "이름을 입력해 주세요.".takeIf { name.isBlank() },
    phone = when { phone.isBlank() -> "전화번호를 입력해 주세요."; !ContactRules.validPhone(phone) -> "전화번호는 숫자 8~15자리로 입력해 주세요."; else -> null },
    email = when { email.isBlank() -> "이메일을 입력해 주세요."; !ContactRules.validEmail(email) -> "이메일 주소를 확인해 주세요."; else -> null },
)
/** 보기 모드 맨 위의 명함 면(contract "프로필 사진" 2026-10-26 사용자 결정): 사진·이름과 소개 링크·직무·소속·연락처. 이력은 [historyCards]로 카드 아래에 따로 그린다. */
fun ProfileFormState.cardState(photoUrl: String?) = CardState("profile", name.ifBlank { "이름 없음" }, jobsText, "", "", introLink.trim().ifEmpty { null },
    (listOf(ContactState(phoneId, "phone", "", phone), ContactState(emailId, "email", "", email, emailVerified)) + extras.map { ContactState(it.id, it.kind, "", it.value, it.verified) }).filter { it.value.isNotBlank() },
    emptyList(), histories.currentOrganization(), photoUrl)
/** First profile entry: accordion sections in this order; completed ones collapse to a row that reopens on tap. */
enum class SetupStep(val label: String) { NAME("이름"), JOBS("직무"), PHONE("전화번호"), EMAIL("이메일") }

/**
 * The in-place edit form: required phone and email, added contacts by kind, 직무 tags, histories from the step funnel, and the version for `If-Match`.
 * [emailVerifiedValue] (contract "연락처 인증"): the address the server verified, or null; the mark stays while [email] is that address (case-insensitive).
 */
data class ProfileFormState(
    /** [introLink] is the 소개 링크 field as typed; blank means none. */
    val name: String, val jobs: List<ProfileJob>, val introLink: String, val phone: String, val email: String,
    val extras: List<ProfileExtraContact>, val histories: List<AccountHistory>, val version: Int, val phoneId: String, val emailId: String,
    val emailVerifiedValue: String? = null,
) {
    val emailVerified: Boolean get() = emailVerifiedValue != null && email.trim().equals(emailVerifiedValue.trim(), ignoreCase = true)
    /** The email was verified and is being changed: saving will drop the mark. */
    val emailUnverifying: Boolean get() = emailVerifiedValue != null && !emailVerified
    /** `인증번호 받기` makes sense: a well-formed address that is not the verified one. */
    val emailCanVerify: Boolean get() = !emailVerified && ContactRules.validEmail(email)
    companion object {
        const val MAX_JOBS = 10
        fun from(profile: AccountProfile): ProfileFormState {
            val phone = profile.contacts.firstOrNull { it.kind == "phone" }
            val email = profile.contacts.firstOrNull { it.kind == "email" }
            return ProfileFormState(profile.name, profile.jobs, profile.introLink.orEmpty(), phone?.value.orEmpty(), email?.value.orEmpty(),
                profile.contacts.filter { it.id != phone?.id && it.id != email?.id }.map { ProfileExtraContact(it.id, it.kind, ContactRules.shown(it.kind, it.value), it.value.takeIf { _ -> it.verified }) }, // a profile URL saved before 2026-10-26 reads as its username
                profile.histories, profile.version, phone?.id ?: UUID.randomUUID().toString(), email?.id ?: UUID.randomUUID().toString(),
                emailVerifiedValue = email?.value?.takeIf { email.verified })
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
 * One history being entered step by step (contract "이력 (History) — 역할 매칭", one funnel since 2026-11-03): 어디에서(조직·프로그램·활동 통합 검색,
 * 없으면 종류를 고르고 이름 직접 입력) → 역할(그 단위의 역할 목록, 없으면 직접 입력) → 기간 → 설명 → 확인. A role belongs to its unit, so changing
 * the unit drops the role. [organization]·[program]·[activity] are the saved history's convenience refs (summary only; the server fills them again).
 */
data class HistoryDraft(
    val id: String = UUID.randomUUID().toString(),
    val ownerKind: String = "organization", val owner: ActivityInfoRef? = null, val ownerName: String = "",
    val role: ActivityInfoRef? = null, val roleName: String = "",
    val organization: ActivityInfoRef? = null, val program: ActivityInfoRef? = null, val activity: ActivityInfoRef? = null,
    val start: LocalDate? = null, val end: LocalDate? = null, val ongoing: Boolean = true, val description: String = "",
    val approval: String = "approved", val approvalReason: String? = null, val verification: String = "unverified", val evidence: List<EvidenceMeta> = emptyList(),
) {
    enum class Step { UNIT, ROLE, PERIOD, DESCRIPTION, SUMMARY }
    val steps get() = Step.entries
    /** The unit was typed, not picked: its kind is asked too, and there is no role list to search. */
    val customUnit get() = owner == null
    fun error(step: Step): String? = when (step) {
        Step.UNIT -> "조직·프로그램·활동을 고르거나 적어 주세요.".takeIf { owner == null && ownerName.isBlank() }
        Step.ROLE -> "역할을 고르거나 적어 주세요.".takeIf { role == null && roleName.isBlank() }
        Step.PERIOD -> when { start == null -> "시작일을 골라 주세요."; !ongoing && end != null && end < start -> "종료일은 시작일 이후로 골라 주세요."; else -> null }
        else -> null
    }
    val history get() = AccountHistory(id, role, if (role == null) roleName.trim() else "", HistoryOwner(ownerKind, owner?.id, owner?.name ?: ownerName.trim()),
        organization, program, activity,
        start?.toString().orEmpty(), if (ongoing) null else end?.toString(), description.trim(), approval, approvalReason, verification, evidence)
    /** `활동 · 2026.03 – 진행 중`: the unit kind and the period. */
    val summaryLine get() = unitLabel(ownerKind) + " · " + historyPeriod(start, if (ongoing) null else end, ongoing)
    /** What the summary shows: `귀속 단위 · 역할` and the 소속 line, from the draft itself. */
    val title get() = history.title
    val affiliation get() = history.affiliation
    val hasCustomEntry get() = history.hasCustomEntry
    /** A picked unit replaces the typed name and, being a different unit, drops the role; the hit's parents ([organization]·[program]) feed the summary's 소속 line. */
    fun pickedUnit(kind: String, id: Long, name: String, organization: ActivityInfoRef? = null, program: ActivityInfoRef? = null): HistoryDraft =
        if (owner?.id == id && ownerKind == kind) this
        else copy(ownerKind = kind, owner = ActivityInfoRef(id, name), ownerName = "", role = null, roleName = "", organization = organization, program = program, activity = null)
    fun clearedUnit() = copy(owner = null, role = null, roleName = "", organization = null, program = null, activity = null)
    fun typedUnit(name: String) = copy(ownerName = name, owner = null, role = null, roleName = "", organization = null, program = null, activity = null)
    fun pickedRole(id: Long, name: String) = copy(role = ActivityInfoRef(id, name), roleName = "")
    fun clearedRole() = copy(role = null)
    fun typedRole(name: String) = copy(roleName = name, role = null)

    companion object {
        fun from(h: AccountHistory) = HistoryDraft(h.id, h.owner.kind, h.owner.id?.let { ActivityInfoRef(it, h.owner.name) }, if (h.owner.isCustom) h.owner.name else "",
            h.role, if (h.role == null) h.roleName else "", h.organization, h.program, h.activity,
            runCatching { LocalDate.parse(h.startDate.take(10)) }.getOrNull(), h.endDate?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() },
            h.endDate == null, h.description, h.approval, h.approvalReason, h.verification, h.evidence)
    }
}
/** 조직·프로그램·활동 as the funnel names a unit kind (`organization`·`program`·`activity`). */
fun unitLabel(kind: String?) = when (kind) { "activity" -> "활동"; "program" -> "프로그램"; else -> "조직" }
/** One history as the profile list shows it; computed outside the composable so pages render State only. */
data class HistoryRowState(val id: String, val title: String, val detail: String, val verified: Boolean, val approvalTag: String?, val saved: Boolean)
fun AccountHistory.rowState(saved: Boolean) = HistoryRowState(id, title,
    listOf(affiliation, periodText(startDate, endDate)).filter { it.isNotEmpty() }.joinToString(" · "), isVerified, approvalTag, saved)
/** 직접 입력했거나 승인 전인 이력의 승인 상태 태그, 나머지는 null. */
private val AccountHistory.approvalTag get() = if (hasCustomEntry || !isApproved) when (approval) { "approved" -> "승인됨"; "rejected" -> "거절됨"; else -> "승인 대기" } else null
/** `2026.03 – 2026.06`, 진행 중이면 `2026.03 – 진행 중`. */
fun periodText(start: String, end: String?): String {
    val month = { text: String -> text.take(7).replace('-', '.') }
    return "${month(start)} – ${end?.let(month) ?: "진행 중"}"
}
/** A saved version as the detail screen shows it (no entity types in the rendering code). */
data class VersionDetailView(val version: Int, val createdAt: String, val name: String, val job: String,
    val contacts: List<Pair<String, String>>, val histories: List<CardHistoryState>)
fun ProfileVersionDetail.view() = VersionDetailView(version, createdAt, profile.name, profile.job,
    profile.contacts.map { it.label to it.value }, profile.histories.historyCards())
/** 내 프로필 이력을 명함 이력 행(`CardHistoryRow`)으로(contract "내 프로필 활동 이력 = 명함 이력"): 승인 대기·거절은 태그로 남긴다. */
fun List<AccountHistory>.historyCards() = map {
    CardHistoryState(it.id, it.title, it.affiliation, periodText(it.startDate, it.endDate), it.isVerified, it.description, it.approvalTag, it.activity?.id?.toString())
}

/** The funnel sheet: which draft is open, whether the history exists on the server (evidence can be attached), and the picker search. */
data class FunnelState(val draft: HistoryDraft, val saved: Boolean, val step: HistoryDraft.Step, val tried: Boolean = false,
    val search: PickerSearchState = PickerSearchState(), val evidenceBusy: Boolean = false, val evidenceError: String? = null,
    /** The draft as opened; leaving with other answers asks first (contract "뒤로 가기"). Evidence is already on the server, so it does not count. */
    val original: HistoryDraft = draft) {
    val edited get() = draft.copy(evidence = original.evidence, verification = original.verification) != original
}
data class PickerSearchState(val query: String = "", val items: List<ActivityInfoItem> = emptyList(), val loading: Boolean = false, val failed: Boolean = false)

/** 변경 이력 screens. */
data class VersionsState(val items: List<Pair<Int, String>> = emptyList(), val nextCursor: Int? = null,
    val loading: Boolean = true, val error: String? = null, val detail: VersionDetailView? = null,
    val detailError: String? = null, val restoring: Boolean = false)
