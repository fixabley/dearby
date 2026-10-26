package com.dearby.nativeapp.app

import android.content.ActivityNotFoundException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountClient
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.shared.config.ConsentVersions
import com.dearby.nativeapp.entities.account.model.AccountContact
import com.dearby.nativeapp.entities.account.model.AccountHistory
import com.dearby.nativeapp.entities.account.model.AccountProfile
import com.dearby.nativeapp.entities.account.model.ContactRules
import com.dearby.nativeapp.features.oauth.OAuthBrowser
import com.dearby.nativeapp.shared.config.ContactLinkRedirect
import com.dearby.nativeapp.pages.profile.FunnelState
import com.dearby.nativeapp.pages.profile.HistoryDraft
import com.dearby.nativeapp.pages.profile.PickerSearchState
import com.dearby.nativeapp.pages.profile.ProfileErrors
import com.dearby.nativeapp.pages.profile.errors
import com.dearby.nativeapp.pages.profile.ProfileFormState
import com.dearby.nativeapp.pages.profile.ProfilePhase
import com.dearby.nativeapp.pages.profile.ProfileViewState
import com.dearby.nativeapp.pages.profile.VersionsState
import com.dearby.nativeapp.pages.profile.rowState
import com.dearby.nativeapp.pages.profile.view
import com.dearby.nativeapp.widgets.profile.profileFields.EmailCodeEntry
import com.dearby.nativeapp.widgets.profile.profileFields.ProfileContactKinds
import com.dearby.nativeapp.widgets.profile.profileFields.ProfileExtraContact
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Account deletion in flight, and the one-time result to show. */
data class DeletionState(val deleting: Boolean = false, val notice: String? = null)

/** SavedStateHandle key: GitHub's page is another app, so "the editor started GitHub 연결" must outlive process death meanwhile (contract "연락처 인증"). */
private const val GITHUB_STARTED = "github.started"

/**
 * 내 프로필 (contract "프로필과 버전 이력"): the data profile edited in place with `If-Match`, saved versions, the history
 * funnel with picker searches, and evidence uploads. A failed save keeps what was typed. 연락처 인증 (contract "연락처 인증") verifies the GitHub and
 * email contacts in place; [saved] only remembers that GitHub 연결 is under way, so a recreated process reopens the editor.
 */
class ProfileViewModel(private val account: AccountViewModel, private val saved: SavedStateHandle) : ViewModel() {
    // Consumed once: after process death while GitHub's page was open the editor comes back; the redirect itself is handled whether or not this was set.
    private val mutable = MutableStateFlow(ProfileViewState(editing = saved.remove<Boolean>(GITHUB_STARTED) == true))
    val state = mutable.asStateFlow()
    private val funnelState = MutableStateFlow<FunnelState?>(null)
    val funnel = funnelState.asStateFlow()
    private val versionsState = MutableStateFlow(VersionsState())
    val versions = versionsState.asStateFlow()
    private val deletionState = MutableStateFlow(DeletionState())
    val deletion = deletionState.asStateFlow()
    private var searchJob: Job? = null

    /** What the server would receive, compared with the loaded profile: drives the 저장/원래대로 bar. */
    val changed get() = mutable.value.let { it.form != null && it.profile != null && profile(it.form) != profile(ProfileFormState.from(it.profile)) }

    fun load() = viewModelScope.launch {
        if (account.state.value.phase != AccountPhase.SIGNED_IN) { mutable.value = ProfileViewState(ProfilePhase.SIGNED_OUT); account.consentKnown(null); return@launch }
        if (mutable.value.profile == null) mutable.update { it.copy(phase = ProfilePhase.LOADING) }
        try {
            val stored = account.authorized { account.client.profile(it) }
            // A reload resets the form unless an edit is pending. A read that started before a verification's write came back (an older version) is dropped.
            mutable.update {
                if (stored.version < (it.profile?.version ?: 0)) return@update it
                it.copy(phase = ProfilePhase.LOADED, profile = stored, form = if (changed && !it.saving) it.form else ProfileFormState.from(stored), errors = null, error = null,
                    consentRequired = stored.consentRequired, consentError = null)
            }
            account.consentKnown(!stored.consentRequired)
        } catch (e: AccountException) {
            mutable.update { ProfileViewState(if (e.error == AccountError.UNAUTHORIZED) ProfilePhase.SIGNED_OUT else ProfilePhase.FAILED) }
            account.consentKnown(null)
        }
    }
    /** 개인정보 동의 (contract "개인정보 동의"): records both versions, then reloads so the profile (or the setup funnel) can start. */
    fun consent() {
        if (mutable.value.consenting) return
        mutable.update { it.copy(consenting = true, consentError = null) }
        viewModelScope.launch {
            try {
                account.authorized { account.client.consent(ConsentVersions.PRIVACY, ConsentVersions.TERMS, it) }
                mutable.update { it.copy(consenting = false, consentRequired = false) }
                load()
            } catch (e: AccountException) {
                mutable.update { it.copy(consenting = false, consentError = when (e.error) {
                    AccountError.UNAUTHORIZED -> { "로그인이 만료됐어요. 다시 로그인해 주세요." }
                    AccountError.BAD_REQUEST -> "앱이 알고 있는 약관 버전이 오래됐어요. 앱을 업데이트한 뒤 다시 시도해 주세요."
                    else -> "동의를 저장하지 못했어요. 잠시 후 다시 시도해 주세요."
                }) }
                if (e.error == AccountError.UNAUTHORIZED) mutable.update { it.copy(phase = ProfilePhase.SIGNED_OUT) }
            }
        }
    }
    /** 수정/완료 in the top bar (contract "프로필 읽기/수정 모드"); 완료 with changes asks first in the page, 버리기 reverts then calls this with false. */
    fun edit(on: Boolean) = mutable.update { it.copy(editing = on) }
    fun change(form: ProfileFormState) = mutable.update { current ->
        val problems = current.errors?.let { errors(form) }
        current.copy(form = form, errors = problems, error = if (problems == ProfileErrors()) null else current.error)
    }
    fun addContact(kind: String) = mutable.update { it.copy(form = it.form?.let { f -> f.copy(extras = f.extras + ProfileExtraContact(UUID.randomUUID().toString(), kind, "")) }) }
    fun removeHistory(id: String) = mutable.update { it.copy(form = it.form?.let { f -> f.copy(histories = f.histories.filter { h -> h.id != id }) }) }
    /** 원래대로: back to the loaded profile; after a 409 it also fetches the newer one. */
    fun revert() {
        mutable.update { it.copy(form = it.profile?.let(ProfileFormState::from), errors = null, error = null) }
        load()
    }
    fun save() {
        val current = mutable.value
        val form = current.form ?: return
        val problems = errors(form)
        if (problems != ProfileErrors()) { mutable.value = current.copy(errors = problems, error = "빨간 안내를 확인해 주세요."); return }
        mutable.value = current.copy(errors = null, saving = true, error = null)
        viewModelScope.launch {
            try {
                val saved = account.authorized { account.client.saveProfile(profile(form), form.version, it) }
                // Save success returns to read mode (contract "프로필 읽기/수정 모드").
                mutable.update { it.copy(profile = saved, form = ProfileFormState.from(saved), saving = false, editing = false) }
            } catch (e: AccountException) {
                // 403 CONSENT_REQUIRED (contract "개인정보 동의"): the consent screen comes back instead of an error line.
                if (e.error == AccountError.FORBIDDEN && e.code == "CONSENT_REQUIRED") { mutable.update { it.copy(saving = false, consentRequired = true) }; account.consentKnown(false); return@launch }
                mutable.update { it.copy(saving = false, error = message(e, "저장하지 못했어요. 잠시 후 다시 시도해 주세요.")) }
            }
        }
    }

    // --- 연락처 인증 (contract "연락처 인증", 2026-10-26): GitHub 연결 (start → Custom Tab → dearby://contacts/github → complete) and 이메일 인증번호 (challenge → code → verify).
    // Both answer the new Profile, merged into the form so other unsaved edits stay and the next PUT's If-Match is current. 503 hides that button for this session.
    /** `GitHub으로 연결`: GitHub's authorize page in a Custom Tab; the result comes back through [githubReturned], or never if the person leaves the tab. */
    fun linkGithub(browser: OAuthBrowser) {
        if (mutable.value.verify.githubBusy) return
        mutable.update { it.copy(verify = it.verify.copy(githubBusy = true)) }
        viewModelScope.launch {
            try {
                val url = account.authorized { account.client.githubStart(it) }
                if (!url.startsWith("https://")) throw AccountException(AccountError.UNAVAILABLE)
                saved[GITHUB_STARTED] = true
                try { browser.open(url) } catch (e: ActivityNotFoundException) {
                    saved.remove<Boolean>(GITHUB_STARTED)
                    Tracking.contactVerify("github", "failed")
                    mutable.update { it.copy(notice = "브라우저를 열 수 없어요. 브라우저 앱을 확인해 주세요.") }
                }
            } catch (e: AccountException) {
                verifyFailed("github", e, GITHUB_FAILED)
            } finally { mutable.update { it.copy(verify = it.verify.copy(githubBusy = false)) } }
        }
    }
    /** The `dearby://contacts/github` redirect: the editor comes back in front; a code is spent on `complete`, `cancelled` passes quietly, `failed` says so. */
    fun githubReturned(redirect: ContactLinkRedirect) {
        saved.remove<Boolean>(GITHUB_STARTED)
        mutable.update { it.copy(editing = true, returned = it.returned + 1) }
        when (redirect) {
            ContactLinkRedirect.Cancelled -> Tracking.contactVerify("github", "cancelled")
            ContactLinkRedirect.Failed -> { Tracking.contactVerify("github", "failed"); mutable.update { it.copy(notice = GITHUB_FAILED) } }
            is ContactLinkRedirect.Code -> {
                mutable.update { it.copy(verify = it.verify.copy(githubBusy = true)) }
                viewModelScope.launch {
                    try {
                        verified("github", account.authorized { account.client.githubComplete(redirect.code, it) })
                    } catch (e: AccountException) {
                        verifyFailed("github", e, GITHUB_FAILED)
                    } finally { mutable.update { it.copy(verify = it.verify.copy(githubBusy = false)) } }
                }
            }
        }
    }
    /** `인증번호 받기`·`다시 받기`: mails a code to the typed address; the entry opens (or stays) with 다시 받기 asleep for a minute, also after a 429. */
    fun requestEmailCode() {
        val current = mutable.value
        val email = current.form?.email?.trim()?.takeIf { ContactRules.validEmail(it) } ?: return
        if (current.verify.emailBusy) return
        mutable.update { it.copy(verify = it.verify.copy(emailBusy = true, entry = it.verify.entry.copy(error = null))) }
        viewModelScope.launch {
            try {
                val challenge = account.authorized { account.client.emailChallenge(email, it) }
                mutable.update { it.copy(verify = it.verify.copy(challengeId = challenge.id, entry = EmailCodeEntry(resendAt = System.currentTimeMillis() + RESEND_AFTER))) }
            } catch (e: AccountException) {
                // 429 (1분당 1회·하루 10회): the server's sentence, and the open entry waits another minute.
                if (e.error == AccountError.RATE_LIMITED) mutable.update { it.copy(notice = e.detail ?: "인증번호는 1분에 한 번 받을 수 있어요.", verify = it.verify.copy(entry = it.verify.entry.copy(resendAt = System.currentTimeMillis() + RESEND_AFTER))) }
                else verifyFailed("email", e, "인증번호를 보내지 못했어요. 잠시 후 다시 시도해 주세요.")
            } finally { mutable.update { it.copy(verify = it.verify.copy(emailBusy = false)) } }
        }
    }
    /** Digits only, six at most. */
    fun changeEmailCode(text: String) = mutable.update { it.copy(verify = it.verify.copy(entry = it.verify.entry.copy(code = text.filter(Char::isDigit).take(EmailCodeEntry.LENGTH), error = null))) }
    /** `확인`: a wrong code shows the tries left under the field; an expired or discarded challenge closes the entry for a new `인증번호 받기`. */
    fun verifyEmail() {
        val current = mutable.value.verify
        val challengeId = current.challengeId ?: return
        if (current.emailBusy || !current.entry.complete) return
        mutable.update { it.copy(verify = it.verify.copy(emailBusy = true, entry = it.verify.entry.copy(error = null))) }
        viewModelScope.launch {
            try {
                verified("email", account.authorized { account.client.emailVerify(challengeId, current.entry.code, it) })
            } catch (e: AccountException) {
                when {
                    e.error == AccountError.BAD_REQUEST && e.code == "CODE_MISMATCH" -> { Tracking.contactVerify("email", "failed"); mutable.update { it.copy(verify = it.verify.copy(entry = it.verify.entry.copy(error = e.detail ?: "인증번호가 맞지 않아요."))) } }
                    e.error == AccountError.BAD_REQUEST && e.code == "CHALLENGE_EXPIRED" -> { Tracking.contactVerify("email", "failed"); mutable.update { it.copy(notice = e.detail ?: "인증번호가 만료됐어요. 다시 받아 주세요.", verify = it.verify.copy(challengeId = null, entry = EmailCodeEntry())) } }
                    else -> verifyFailed("email", e, "이메일을 인증하지 못했어요. 잠시 후 다시 시도해 주세요.")
                }
            } finally { mutable.update { it.copy(verify = it.verify.copy(emailBusy = false)) } }
        }
    }
    fun dismissNotice() = mutable.update { it.copy(notice = null) }
    /**
     * The Profile a verification answered (a version up): kept as the loaded profile and merged into the form — only the verified [kind]'s contact takes the
     * server's value, a row of that kind the server appended (a GitHub row that was not there) is added, every other typed, edited or removed row stays as it
     * is (with its mark refreshed), and the email entry closes.
     */
    private fun verified(kind: String, profile: AccountProfile) {
        Tracking.contactVerify(kind, "ok")
        mutable.update { it.copy(profile = profile, form = it.form?.merged(kind, profile) ?: ProfileFormState.from(profile), verify = it.verify.copy(challengeId = null, entry = EmailCodeEntry())) }
    }
    private fun ProfileFormState.merged(kind: String, profile: AccountProfile): ProfileFormState {
        val fresh = ProfileFormState.from(profile)
        val email = profile.contacts.firstOrNull { it.kind == "email" }
        val rows = extras.map { row ->
            val contact = profile.contacts.firstOrNull { it.id == row.id } ?: return@map row
            val value = if (contact.kind == kind && contact.verified) ContactRules.shown(contact.kind, contact.value) else row.value
            row.copy(value = value, verifiedValue = contact.value.takeIf { contact.verified })
        } + fresh.extras.filter { added -> added.kind == kind && extras.none { it.id == added.id } }
        return copy(version = profile.version, email = if (kind == "email" && email?.verified == true) email.value else this.email, emailVerifiedValue = fresh.emailVerifiedValue, extras = rows)
    }
    /** The app brought the editor back after a GitHub return. */
    fun consumeReturned() = mutable.update { it.copy(returned = 0) }
    /** 403 CONSENT_REQUIRED brings the consent screen back; 503 switches that feature off for this session with a notice; the rest is a one-line notice. */
    private fun verifyFailed(kind: String, e: AccountException, fallback: String) {
        Tracking.contactVerify(kind, "failed")
        when {
            e.error == AccountError.FORBIDDEN && e.code == "CONSENT_REQUIRED" -> { mutable.update { it.copy(consentRequired = true) }; account.consentKnown(false) }
            e.error == AccountError.TURNED_OFF -> mutable.update { it.copy(notice = "지금은 쓸 수 없어요. 연락처를 직접 입력해 주세요.",
                verify = if (kind == "github") it.verify.copy(githubAvailable = false) else it.verify.copy(emailAvailable = false)) }
            else -> mutable.update { it.copy(notice = message(e, fallback)) }
        }
    }

    // --- 프로필 사진 (contract "프로필 사진"): saved on its own right away, like evidence; 403 CONSENT_REQUIRED brings the consent screen back.
    /** Uploads the cropped square; [done] runs once the server has it (the crop screen closes). */
    fun uploadPhoto(jpeg: ByteArray, done: () -> Unit) = photo("사진을 올리지 못했어요. 잠시 후 다시 시도해 주세요.") {
        val url = account.authorized { account.client.uploadProfilePhoto(jpeg, it) }
        account.photos.put(url, jpeg)
        mutable.update { it.copy(profile = it.profile?.copy(photoUrl = url)) }
        done()
    }
    fun deletePhoto() = photo("사진을 지우지 못했어요. 잠시 후 다시 시도해 주세요.") {
        account.authorized { account.client.deleteProfilePhoto(it) }
        mutable.update { it.copy(profile = it.profile?.copy(photoUrl = null)) }
    }
    fun dismissPhotoError() = mutable.update { it.copy(photoError = null) }
    /** The picked file could not be opened as an image. */
    fun photoFailed(message: String) = mutable.update { it.copy(photoError = message) }
    private fun photo(fallback: String, call: suspend () -> Unit) {
        if (mutable.value.photoBusy) return
        mutable.update { it.copy(photoBusy = true, photoError = null) }
        viewModelScope.launch {
            try { call(); mutable.update { it.copy(photoBusy = false) } }
            catch (e: AccountException) {
                if (e.error == AccountError.FORBIDDEN && e.code == "CONSENT_REQUIRED") { mutable.update { it.copy(photoBusy = false, consentRequired = true) }; account.consentKnown(false); return@launch }
                mutable.update { it.copy(photoBusy = false, photoError = message(e, fallback)) }
            }
        }
    }

    // --- history funnel
    /** Rows for the profile list: the form's histories, marked saved when the server has them. */
    val rows get() = mutable.value.let { s -> s.form?.histories?.map { h -> h.rowState(s.profile?.histories?.any { it.id == h.id } == true) }.orEmpty() }
    fun openHistory(id: String?) {
        val history = id?.let { hid -> mutable.value.form?.histories?.firstOrNull { it.id == hid } }
        val saved = history != null && mutable.value.profile?.histories?.any { it.id == history.id } == true
        funnelState.value = FunnelState(history?.let(HistoryDraft::from) ?: HistoryDraft(), saved, if (saved) HistoryDraft.Step.SUMMARY else HistoryDraft.Step.KIND)
    }
    fun closeFunnel() { searchJob?.cancel(); funnelState.value = null }
    fun changeDraft(draft: HistoryDraft) = funnelState.update { it?.copy(draft = draft) }
    fun goTo(step: HistoryDraft.Step) {
        funnelState.update { it?.copy(step = step, tried = false, search = PickerSearchState()) }
        if (step.isPicker) search("")
    }
    fun next() {
        val current = funnelState.value ?: return
        if (current.draft.error(current.step) != null) { funnelState.value = current.copy(tried = true); return }
        if (current.step == HistoryDraft.Step.SUMMARY) {
            mutable.update { it.copy(form = it.form?.put(current.draft.history)) }
            closeFunnel()
        } else {
            val steps = current.draft.steps
            goTo(steps[steps.indexOf(current.step) + 1])
        }
    }
    /** Picker search for the current step, debounced; programs narrow to the chosen organization, activities search the whole catalogue. */
    fun search(query: String) {
        funnelState.update { it?.copy(search = it.search.copy(query = query, loading = true, failed = false)) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            val current = funnelState.value ?: return@launch
            val (kind, parent) = when (current.step) {
                HistoryDraft.Step.ORGANIZATION -> AccountClient.ActivityInfoKind.ORGANIZATIONS to null
                HistoryDraft.Step.PROGRAM -> AccountClient.ActivityInfoKind.PROGRAMS to current.draft.organization?.id
                HistoryDraft.Step.ACTIVITY -> AccountClient.ActivityInfoKind.ACTIVITIES to null
                HistoryDraft.Step.ROLE -> AccountClient.ActivityInfoKind.ROLES to null
                else -> return@launch
            }
            try {
                val page = account.authorized { account.client.searchActivityInfo(kind, query, parent, it) }
                funnelState.update { it?.copy(search = it.search.copy(items = page.items, loading = false)) }
            } catch (e: AccountException) {
                funnelState.update { it?.copy(search = it.search.copy(loading = false, failed = true)) }
            }
        }
    }
    /** Evidence for a saved history (the server moves verification to pending); the profile reloads afterwards. */
    fun upload(fileName: String, contentType: String, bytes: ByteArray) {
        val current = funnelState.value ?: return
        if (bytes.size > 5 * 1024 * 1024) { funnelState.value = current.copy(evidenceError = "파일은 5MB까지 올릴 수 있어요."); return }
        funnelState.value = current.copy(evidenceBusy = true, evidenceError = null)
        viewModelScope.launch {
            val error = try {
                account.authorized { account.client.uploadEvidence(current.draft.id, fileName, contentType, bytes, it) }; null
            } catch (e: AccountException) { message(e, "파일을 올리지 못했어요. 잠시 후 다시 시도해 주세요.") }
            refreshEvidence(error)
        }
    }
    fun deleteEvidence(evidenceId: String) {
        val current = funnelState.value ?: return
        funnelState.value = current.copy(evidenceBusy = true, evidenceError = null)
        viewModelScope.launch {
            val error = try {
                account.authorized { account.client.deleteEvidence(current.draft.id, evidenceId, it) }; null
            } catch (e: AccountException) { message(e, "파일을 지우지 못했어요. 잠시 후 다시 시도해 주세요.") }
            refreshEvidence(error)
        }
    }
    private suspend fun refreshEvidence(error: String?) {
        load().join()
        val history = mutable.value.profile?.histories?.firstOrNull { it.id == funnelState.value?.draft?.id }
        funnelState.update { it?.copy(evidenceBusy = false, evidenceError = error, draft = history?.let { h -> it.draft.copy(evidence = h.evidence, verification = h.verification) } ?: it.draft) }
        if (history != null) mutable.update { it.copy(form = it.form?.put(history)) }
    }

    // --- versions
    fun loadVersions(reset: Boolean) = viewModelScope.launch {
        if (reset) versionsState.value = VersionsState()
        versionsState.update { it.copy(loading = true) }
        try {
            val page = account.authorized { account.client.profileVersions(versionsState.value.nextCursor, it) }
            versionsState.update { it.copy(items = it.items + page.items.map { v -> v.version to v.createdAt }, nextCursor = page.nextCursor, loading = false) }
        } catch (e: AccountException) { versionsState.update { it.copy(loading = false, error = "변경 이력을 불러오지 못했어요.") } }
    }
    fun openVersion(version: Int) = viewModelScope.launch {
        versionsState.update { it.copy(detail = null, detailError = null, error = null) }
        try { versionsState.update { s -> s.copy(detail = account.authorized { account.client.profileVersion(version, it) }.view()) } }
        catch (e: AccountException) { versionsState.update { it.copy(detailError = "이 버전을 불러오지 못했어요.") } }
    }
    fun closeVersion() = versionsState.update { it.copy(detail = null, detailError = null, error = null) }
    /** Returns true when restored (the caller closes the screens). */
    fun restore(version: Int, done: () -> Unit) = viewModelScope.launch {
        versionsState.update { it.copy(restoring = true, error = null) }
        try {
            val restored = account.authorized { account.client.restoreProfile(version, it) }
            mutable.update { it.copy(profile = restored, form = ProfileFormState.from(restored), errors = null, error = null) }
            versionsState.update { it.copy(restoring = false) }
            done()
        } catch (e: AccountException) { versionsState.update { it.copy(restoring = false, error = message(e, "되돌리지 못했어요. 잠시 후 다시 시도해 주세요.")) } }
    }

    /** "계정 삭제" after the confirmation dialog; taps while a request runs are ignored. The passkey cleanup hint only when the account [hadPasskeys]. */
    fun deleteAccount(hadPasskeys: Boolean) {
        if (deletionState.value.deleting) return
        deletionState.value = DeletionState(deleting = true)
        viewModelScope.launch {
            val notice = try {
                account.deleteAccount()
                if (hadPasskeys) "계정을 삭제했어요. 기기에 남은 패스키는 비밀번호 관리자에서 지울 수 있어요." else "계정을 삭제했어요."
            } catch (e: AccountException) {
                if (e.error == AccountError.UNAUTHORIZED) "로그인이 만료됐어요. 다시 로그인해 주세요." else "계정을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요."
            }
            deletionState.value = DeletionState(notice = notice)
        }
    }
    fun dismissDeletionNotice() = deletionState.update { it.copy(notice = null) }

    private fun message(e: AccountException, fallback: String) = when (e.error) {
        AccountError.UNAUTHORIZED -> { mutable.update { it.copy(phase = ProfilePhase.SIGNED_OUT) }; "로그인이 만료됐어요. 다시 로그인해 주세요." }
        AccountError.BAD_REQUEST -> e.detail ?: "입력한 내용을 확인해 주세요."
        AccountError.CONFLICT -> "프로필이 다른 곳에서 바뀌었어요. ‘원래대로’를 눌러 새로 불러온 뒤 다시 고쳐 주세요."
        else -> fallback
    }
    private fun errors(form: ProfileFormState) = form.errors()
    /** `PUT /v1/profile` body. Empty added contacts are left out. */
    private fun profile(form: ProfileFormState): AccountProfile {
        val labels = ProfileContactKinds.associate { (kind, label, _) -> kind to label }
        return AccountProfile(form.name.trim(), "", form.introLink.trim().ifEmpty { null },
            listOf(AccountContact(form.phoneId, "phone", "전화번호", form.phone.trim()), AccountContact(form.emailId, "email", "이메일", form.email.trim())) +
                form.extras.filter { it.value.isNotBlank() }.map { AccountContact(it.id, it.kind, labels[it.kind] ?: it.kind, it.value.trim()) },
            form.histories, form.version, form.jobs)
    }
    private var jobSearchJob: Job? = null
    /** 직무 tag picker search, debounced (`GET /v1/jobs`). */
    fun searchJobs(query: String) {
        mutable.update { it.copy(jobSearch = it.jobSearch.copy(query = query, loading = true, failed = false)) }
        jobSearchJob?.cancel()
        jobSearchJob = viewModelScope.launch {
            delay(300)
            try {
                val page = account.authorized { account.client.searchActivityInfo(AccountClient.ActivityInfoKind.JOBS, query, null, it) }
                mutable.update { it.copy(jobSearch = it.jobSearch.copy(items = page.items, loading = false)) }
            } catch (e: AccountException) {
                mutable.update { it.copy(jobSearch = it.jobSearch.copy(loading = false, failed = true)) }
            }
        }
    }
    private companion object {
        const val GITHUB_FAILED = "GitHub 연결에 실패했어요. 다시 시도해 주세요."
        /** 다시 받기 sleeps this long after a mail (the server allows one a minute). */
        const val RESEND_AFTER = 60_000L
    }
    private val HistoryDraft.Step.isPicker get() = this in setOf(HistoryDraft.Step.ORGANIZATION, HistoryDraft.Step.PROGRAM, HistoryDraft.Step.ACTIVITY, HistoryDraft.Step.ROLE)
}
