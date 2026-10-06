package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.AccountContact
import com.dearby.nativeapp.entities.account.model.AccountHistory
import com.dearby.nativeapp.entities.account.model.AccountProfile
import com.dearby.nativeapp.entities.account.model.ContactRules
import com.dearby.nativeapp.pages.profile.HistoryFormState
import com.dearby.nativeapp.pages.profile.ProfileErrors
import com.dearby.nativeapp.pages.profile.ProfileFormState
import com.dearby.nativeapp.pages.profile.ProfilePhase
import com.dearby.nativeapp.pages.profile.ProfileState
import com.dearby.nativeapp.pages.profile.ProfileViewState
import com.dearby.nativeapp.widgets.card.cardContent.CardHistoryState
import com.dearby.nativeapp.widgets.card.cardContent.ContactState
import com.dearby.nativeapp.widgets.profile.profileFields.ProfileContactKinds
import com.dearby.nativeapp.widgets.profile.profileFields.ProfileExtraContact
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Editing session: the form, the messages once save was tried, and the last save failure. */
data class ProfileEditState(val form: ProfileFormState, val errors: ProfileErrors? = null, val saving: Boolean = false, val error: String? = null)

/** 내 프로필 (#149): `GET`/`PUT /v1/profile`. Checks match the API; a failed save keeps what was typed. */
class ProfileViewModel(private val account: AccountViewModel) : ViewModel() {
    private val mutable = MutableStateFlow(ProfileViewState())
    val state = mutable.asStateFlow()
    private val editing = MutableStateFlow<ProfileEditState?>(null)
    val edit = editing.asStateFlow()
    private var saved: AccountProfile? = null
    private var phoneId = ""
    private var emailId = ""

    fun load() = viewModelScope.launch {
        if (account.state.value.phase != AccountPhase.SIGNED_IN) { mutable.value = ProfileViewState(ProfilePhase.SIGNED_OUT); return@launch }
        if (mutable.value.profile == null) mutable.update { it.copy(phase = ProfilePhase.LOADING) }
        mutable.value = try {
            account.authorized { account.client.profile(it) }.also { saved = it }.let { ProfileViewState(ProfilePhase.LOADED, it.toState()) }
        } catch (e: AccountException) {
            ProfileViewState(if (e.error == AccountError.UNAUTHORIZED) ProfilePhase.SIGNED_OUT else ProfilePhase.FAILED)
        }
    }
    fun startEdit() {
        val profile = saved ?: return
        val phone = profile.contacts.firstOrNull { it.kind == "phone" }
        val email = profile.contacts.firstOrNull { it.kind == "email" }
        phoneId = phone?.id ?: newId()
        emailId = email?.id ?: newId()
        editing.value = ProfileEditState(ProfileFormState(profile.name, profile.job, profile.introduction, phone?.value.orEmpty(),
            email?.value ?: account.state.value.email,
            profile.contacts.filter { it.id != phone?.id && it.id != email?.id }.map { ProfileExtraContact(it.id, it.kind, it.value) },
            profile.histories.map { HistoryFormState(it.id, it.title, it.role, date(it.startDate), it.endDate?.let(::date), it.endDate == null, it.description) }))
    }
    // After a save attempt, messages follow the form; the summary clears once everything is fixed.
    fun change(form: ProfileFormState) = editing.update { current ->
        val problems = current?.errors?.let { errors(form) }
        current?.copy(form = form, errors = problems, error = if (problems == ProfileErrors()) null else current.error)
    }
    fun addContact(kind: String) = editing.update { it?.copy(form = it.form.copy(extras = it.form.extras + ProfileExtraContact(newId(), kind, ""))) }
    fun addHistory() = editing.update { it?.copy(form = it.form.copy(histories = it.form.histories + HistoryFormState(newId(), "", "", null, null, true, ""))) }
    fun close() { editing.value = null }
    fun save() {
        val current = editing.value ?: return
        val problems = errors(current.form)
        if (problems != ProfileErrors()) { editing.value = current.copy(errors = problems, error = "빨간 안내를 확인해 주세요."); return }
        editing.value = current.copy(errors = problems, saving = true, error = null)
        viewModelScope.launch {
            try {
                saved = account.authorized { account.client.saveProfile(profile(current.form), it) }
                mutable.value = ProfileViewState(ProfilePhase.LOADED, saved!!.toState())
                editing.value = null
            } catch (e: AccountException) {
                editing.update { it?.copy(saving = false, error = when (e.error) {
                    AccountError.UNAUTHORIZED -> "로그인이 만료됐어요. 다시 로그인해 주세요."
                    AccountError.INVALID_CONTACTS -> "전화번호와 이메일을 확인해 주세요."
                    AccountError.INVALID_INPUT -> "입력한 내용을 확인해 주세요. 링크는 https 주소만 쓸 수 있어요."
                    else -> "저장하지 못했어요. 잠시 후 다시 시도해 주세요."
                }) }
            }
        }
    }

    private fun errors(form: ProfileFormState) = ProfileErrors(
        name = "이름을 입력해 주세요.".takeIf { form.name.isBlank() },
        phone = when { form.phone.isBlank() -> "전화번호를 입력해 주세요."; !ContactRules.validPhone(form.phone) -> "전화번호는 숫자 8~15자리로 입력해 주세요."; else -> null },
        email = when { form.email.isBlank() -> "이메일을 입력해 주세요."; !ContactRules.validEmail(form.email) -> "이메일 주소를 확인해 주세요."; else -> null },
        histories = form.histories.mapNotNull { history ->
            when {
                history.title.isBlank() -> "활동 이름을 입력해 주세요."
                history.start == null -> "시작일을 골라 주세요."
                !history.ongoing && history.end != null && history.end < history.start -> "종료일은 시작일 이후로 골라 주세요."
                else -> null
            }?.let { history.id to it }
        }.toMap(),
    )
    /** `PUT /v1/profile` body. Empty added contacts are left out; dates are saved as picked (`YYYY-MM-DD`). */
    private fun profile(form: ProfileFormState): AccountProfile {
        val labels = ProfileContactKinds.associate { (kind, label, _) -> kind to label }
        return AccountProfile(form.name.trim(), form.job.trim(), form.introduction.trim(),
            listOf(AccountContact(phoneId, "phone", "전화번호", form.phone.trim()), AccountContact(emailId, "email", "이메일", form.email.trim())) +
                form.extras.filter { it.value.isNotBlank() }.map { AccountContact(it.id, it.kind, labels[it.kind] ?: it.kind, it.value.trim()) },
            form.histories.map { AccountHistory(it.id, it.title.trim(), it.role.trim(), it.start.toString(), if (it.ongoing) null else it.end?.toString(), it.description) })
    }
    private fun newId() = UUID.randomUUID().toString()
    private fun date(text: String) = runCatching { LocalDate.parse(text.take(10)) }.getOrNull()
}

private fun AccountProfile.toState() = ProfileState(name, job, introduction, contacts.map { ContactState(it.id, it.kind, it.label, it.value) },
    histories.map { CardHistoryState(it.id, it.title, it.role, ContactRules.period(it.startDate, it.endDate)) })
