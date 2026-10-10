package com.dearby.nativeapp.app

import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.ViewModel
import com.dearby.nativeapp.entities.account.api.AccountClient
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.api.SessionStore
import com.dearby.nativeapp.entities.account.model.AccountSession
import com.dearby.nativeapp.features.passkey.Passkeys
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException

enum class AccountPhase { SIGNED_OUT, WORKING, SIGNED_IN }
data class AccountState(val phase: AccountPhase, val message: String? = null)
private const val UNSUPPORTED = "이 기기에서는 패스키를 쓸 수 없어요. Android 9 이상이 필요해요."

/** Passkey sign-in (contract "패스키 로그인"), asked for only when publishing, sharing or saving a received card. */
class AccountViewModel(val client: AccountClient, private val vault: SessionStore) : ViewModel() {
    private var session: AccountSession? = runCatching { vault.load() }.getOrNull()
    private val refreshing = Mutex()
    private val mutable = MutableStateFlow(AccountState(if (session == null) AccountPhase.SIGNED_OUT else AccountPhase.SIGNED_IN))
    val state = mutable.asStateFlow()

    /** "패스키로 로그인": one of this device's passkeys. */
    suspend fun signIn(passkeys: Passkeys) = enter {
        val challenge = client.authenticationOptions()
        passkeys.get(challenge.options)?.let { client.authenticate(challenge.id, it) }
    }
    /** "새 패스키로 시작": a new passkey, and with it a new account. */
    suspend fun signUp(passkeys: Passkeys) = enter {
        val challenge = client.registrationOptions()
        passkeys.create(challenge.options)?.let { client.register(challenge.id, it) }
    }

    /**
     * A closed prompt (null) goes back quietly; anything else that fails says so on the sheet. The finally
     * also ends WORKING when the screen that started it goes away mid-prompt.
     */
    private suspend fun enter(prompt: suspend () -> AccountSession?) {
        mutable.update { it.copy(phase = AccountPhase.WORKING, message = null) }
        var failure: String? = null
        try {
            prompt()?.let { keep(it); mutable.update { s -> s.copy(phase = AccountPhase.SIGNED_IN) } }
        } catch (e: GetCredentialUnsupportedException) {
            failure = UNSUPPORTED
        } catch (e: CreateCredentialUnsupportedException) {
            failure = UNSUPPORTED
        } catch (e: NoCredentialException) {
            failure = "이 기기에 Dearby 패스키가 없어요. 새 패스키로 시작해 주세요."
        } catch (e: GetCredentialException) {
            failure = "패스키로 로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
        } catch (e: CreateCredentialException) {
            failure = "패스키를 만들지 못했어요. 잠시 후 다시 시도해 주세요."
        } catch (e: AccountException) {
            failure = when (e.error) {
                AccountError.UNAUTHORIZED -> "패스키를 확인하지 못했어요. 다시 시도해 주세요."
                AccountError.RATE_LIMITED -> "요청이 많아요. 잠시 후 다시 시도해 주세요."
                else -> "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
            }
        } catch (e: JSONException) {
            // A response or credential that is not the contract's JSON.
            failure = "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
        } finally {
            mutable.update { if (it.phase == AccountPhase.WORKING) it.copy(phase = AccountPhase.SIGNED_OUT, message = failure) else it }
        }
    }

    /** Signing out always forgets this device's tokens, even if the server call fails. */
    suspend fun signOut() {
        session?.let { runCatching { client.logout(it.refreshToken) } }
        forget()
    }

    /** Runs an owner call. On 401 it refreshes once and retries; if that fails too, the tokens are forgotten. */
    suspend fun <T> authorized(call: suspend (AccountSession) -> T): T {
        val current = session ?: throw AccountException(AccountError.UNAUTHORIZED)
        try { return call(current) } catch (e: AccountException) { if (e.error != AccountError.UNAUTHORIZED) throw e }
        val renewed = refreshed(current) ?: expired()
        try { return call(renewed) } catch (e: AccountException) { if (e.error == AccountError.UNAUTHORIZED) expired(); throw e }
    }
    /**
     * Refresh tokens rotate, so concurrent 401s share one refresh: a call that waited reuses the pair
     * another call already got. A refused refresh yields null; network failures keep the tokens and rethrow.
     */
    private suspend fun refreshed(stale: AccountSession): AccountSession? = refreshing.withLock {
        val current = session ?: return null
        if (current != stale) return current
        try { client.refresh(current.refreshToken).also(::keep) }
        catch (e: AccountException) { if (e.error == AccountError.UNAUTHORIZED) null else throw e }
    }
    private fun expired(): Nothing {
        forget()
        mutable.update { it.copy(message = "로그인이 만료됐어요. 다시 로그인해 주세요.") }
        throw AccountException(AccountError.UNAUTHORIZED)
    }
    /** If the vault cannot be written, the tokens still last until the app closes. */
    private fun keep(tokens: AccountSession) {
        session = tokens
        runCatching { vault.save(tokens) }
    }
    private fun forget() {
        runCatching { vault.clear() }
        session = null
        mutable.update { it.copy(phase = AccountPhase.SIGNED_OUT) }
    }
}
