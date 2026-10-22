package com.dearby.nativeapp.app

import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.LinkedIdentity
import com.dearby.nativeapp.entities.account.model.PasskeyItem
import com.dearby.nativeapp.features.passkey.Passkeys
import com.dearby.nativeapp.pages.settings.SettingsIdentityState
import com.dearby.nativeapp.pages.settings.SettingsBlockState
import com.dearby.nativeapp.pages.settings.SettingsPasskeyState
import com.dearby.nativeapp.pages.settings.SettingsPhase
import com.dearby.nativeapp.pages.settings.SettingsState
import com.dearby.nativeapp.shared.config.AuthProvider
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONException

/** The sentence a 409 `LAST_SIGN_IN_METHOD` turns into (contract "설정"). */
private const val LAST_METHOD = "마지막 로그인 수단은 지울 수 없어요. 패스키나 다른 로그인을 먼저 추가해 주세요."

/**
 * 설정 (contract "설정"): loads `GET /v1/me`, `/v1/me/identities` and `/v1/me/passkeys` through [AccountViewModel.authorized], maps them to
 * display state and runs 로그아웃·연결 해제·패스키 추가/이름 바꾸기/삭제. Apple/Google 연결하기 is [AccountViewModel]'s own link flow; the
 * route reloads when its `linked` count moves. [version] is the build's version·build text for the 정보 section.
 */
class SettingsViewModel(private val account: AccountViewModel, private val version: String) : ViewModel() {
    private val mutable = MutableStateFlow(SettingsState(version = version))
    val state = mutable.asStateFlow()
    private val client get() = account.client
    private val day = DateTimeFormatter.ofPattern("yyyy.M.d").withZone(ZoneId.of("Asia/Seoul"))

    fun load() {
        if (account.state.value.phase != AccountPhase.SIGNED_IN) { mutable.update { SettingsState(SettingsPhase.SIGNED_OUT, version = version) }; return }
        mutable.update { it.copy(phase = if (it.phase == SettingsPhase.LOADED) it.phase else SettingsPhase.LOADING) }
        viewModelScope.launch {
            try {
                val summary = account.authorized { client.me(it) }
                val identities = account.authorized { client.identities(it) }
                val passkeys = account.authorized { client.passkeys(it) }
                val blocks = account.authorized { client.blocks(it) }
                mutable.update {
                    it.copy(phase = SettingsPhase.LOADED, createdAt = day(summary.createdAt), identities = rows(identities), passkeys = passkeys.map(::row),
                        blocks = blocks.map { block -> SettingsBlockState(block.id, block.name, day(block.blockedAt)) })
                }
            } catch (e: AccountException) {
                mutable.update { it.copy(phase = if (e.error == AccountError.UNAUTHORIZED) SettingsPhase.SIGNED_OUT else SettingsPhase.FAILED) }
            } catch (e: JSONException) {
                mutable.update { it.copy(phase = SettingsPhase.FAILED) }
            }
        }
    }

    /** Both providers always show; a missing identity row is "not linked". */
    private fun rows(identities: List<LinkedIdentity>) = AuthProvider.entries.map { provider ->
        val linked = identities.firstOrNull { it.provider == provider.path }
        SettingsIdentityState(provider.path, provider.label, linked?.email, linked?.let { day(it.linkedAt) })
    }
    private fun row(passkey: PasskeyItem) = SettingsPasskeyState(passkey.id, passkey.label, day(passkey.createdAt), passkey.lastUsedAt?.let(::day), passkey.backedUp)
    private fun day(instant: String) = runCatching { day.format(Instant.parse(instant)) }.getOrDefault(instant.take(10))

    /** 로그아웃 after the confirmation: the tokens are forgotten even if the server call fails ([AccountViewModel.signOut]). */
    fun signOut() = perform("로그아웃하지 못했어요. 잠시 후 다시 시도해 주세요.") { account.signOut(); mutable.update { SettingsState(SettingsPhase.SIGNED_OUT, version = version) } }
    fun unlink(key: String) = perform("연결을 해제하지 못했어요. 잠시 후 다시 시도해 주세요.") {
        val provider = AuthProvider.entries.first { it.path == key }
        account.authorized { client.unlinkIdentity(provider, it) }
        load()
    }
    fun renamePasskey(id: String, label: String) = perform("이름을 바꾸지 못했어요. 잠시 후 다시 시도해 주세요.") {
        val renamed = account.authorized { client.renamePasskey(id, label, it) }
        mutable.update { s -> s.copy(passkeys = s.passkeys.map { if (it.id == id) row(renamed) else it }) }
    }
    fun deletePasskey(id: String) = perform("패스키를 삭제하지 못했어요. 잠시 후 다시 시도해 주세요.") {
        account.authorized { client.deletePasskey(id, it) }
        load()
    }
    /** 패스키 추가: the registration options and result both carry the Bearer, so the passkey joins this account (contract "설정"). */
    fun addPasskey(passkeys: Passkeys) = perform("패스키를 만들지 못했어요. 잠시 후 다시 시도해 주세요.") {
        val challenge = account.authorized { client.passkeyOptions(it) }
        val credential = passkeys.create(challenge.options) ?: return@perform
        account.authorized { client.addPasskey(challenge.id, credential, it) }
        load()
    }
    /** 차단 해제 (contract "명함 신고·차단"): the cards removed by the block do not come back. */
    fun unblock(id: String) = perform("차단을 해제하지 못했어요. 잠시 후 다시 시도해 주세요.") {
        account.authorized { client.unblock(id, it) }
        mutable.update { s -> s.copy(blocks = s.blocks.filterNot { it.id == id }) }
    }
    fun dismissNotice() = mutable.update { it.copy(notice = null) }

    /** One request at a time; a 409 `LAST_SIGN_IN_METHOD`, a lost session and a failed passkey prompt become notices, anything else [fallback]. */
    private fun perform(fallback: String, block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, notice = null) }
        viewModelScope.launch {
            val notice = try { block(); null } catch (e: AccountException) {
                when {
                    e.error == AccountError.CONFLICT && e.code == "LAST_SIGN_IN_METHOD" -> LAST_METHOD
                    e.error == AccountError.UNAUTHORIZED -> "로그인이 만료됐어요. 다시 로그인해 주세요.".also { mutable.update { it.copy(phase = SettingsPhase.SIGNED_OUT) } }
                    e.error == AccountError.BAD_REQUEST -> e.detail ?: fallback
                    else -> fallback
                }
            } catch (e: CreateCredentialUnsupportedException) { "이 기기에서는 패스키를 쓸 수 없어요. Android 9 이상이 필요해요." }
            catch (e: CreateCredentialException) { fallback } catch (e: JSONException) { fallback }
            mutable.update { it.copy(busy = false, notice = notice) }
        }
    }
}
