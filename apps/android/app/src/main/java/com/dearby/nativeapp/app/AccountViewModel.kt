package com.dearby.nativeapp.app

import android.content.ActivityNotFoundException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountClient
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.api.SessionStore
import com.dearby.nativeapp.entities.account.model.AccountSession
import com.dearby.nativeapp.entities.account.model.AppleSignIn
import com.dearby.nativeapp.features.apple.AppleBrowser
import com.dearby.nativeapp.features.apple.AppleVerifier
import com.dearby.nativeapp.features.passkey.Passkeys
import com.dearby.nativeapp.shared.config.AppleRedirect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException

enum class AccountPhase { SIGNED_OUT, WORKING, SIGNED_IN }
/** [appleEmail] is set while a first-seen Apple account waits for "새 계정으로 시작" or "기존 패스키 계정에 연결" (may be empty). */
data class AccountState(val phase: AccountPhase, val message: String? = null, val appleEmail: String? = null)
private const val UNSUPPORTED = "이 기기에서는 패스키를 쓸 수 없어요. Android 9 이상이 필요해요."
private const val APPLE_RETRY = "애플 로그인을 마치지 못했어요. 다시 시도해 주세요."
// SavedStateHandle keys: Apple's page is another app, so these must outlive rotation and process death meanwhile.
private const val APPLE_VERIFIER = "apple.verifier"
private const val APPLE_LINK_TOKEN = "apple.linkToken"
private const val APPLE_EMAIL = "apple.email"

/**
 * Passkey and Apple sign-in (contract "패스키 로그인", "애플 로그인"): asked for when publishing, sharing or saving a received
 * card, and offered (never required) on the first-launch intro.
 */
class AccountViewModel(val client: AccountClient, private val vault: SessionStore, private val saved: SavedStateHandle) : ViewModel() {
    private var session: AccountSession? = runCatching { vault.load() }.getOrNull()
    private val refreshing = Mutex()
    private val mutable = MutableStateFlow(AccountState(if (session == null) AccountPhase.SIGNED_OUT else AccountPhase.SIGNED_IN,
        appleEmail = saved.get<String>(APPLE_LINK_TOKEN)?.let { saved.get<String>(APPLE_EMAIL).orEmpty() }))
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
     * "Apple로 계속하기" (web flow): keeps a new verifier, sends its hash and opens Apple's page in a Custom Tab. Nothing
     * waits here: the result comes back as a `dearby://auth/apple` link through [appleReturned], or never if the person
     * leaves the tab, so the phase is SIGNED_OUT again once the tab is open.
     */
    suspend fun startApple(browser: AppleBrowser) = enter(apple = true) {
        val verifier = AppleVerifier.create()
        val url = client.appleStart(AppleVerifier.hash(verifier))
        if (!url.startsWith("https://")) throw AccountException(AccountError.UNAVAILABLE)
        saved[APPLE_VERIFIER] = verifier
        try { browser.open(url) } catch (e: ActivityNotFoundException) {
            saved.remove<String>(APPLE_VERIFIER)
            throw AppleFailure("브라우저를 열 수 없어요. 브라우저 앱을 확인해 주세요.")
        }
        null
    }

    /** Apple's redirect. The verifier is single-use, so it is dropped whatever comes back; a code without one is stale. */
    fun appleReturned(redirect: AppleRedirect) {
        val verifier = saved.remove<String>(APPLE_VERIFIER)
        when (redirect) {
            AppleRedirect.Cancelled -> mutable.update { it.copy(message = null) }
            AppleRedirect.Failed -> mutable.update { it.copy(message = APPLE_RETRY) }
            is AppleRedirect.Code -> if (verifier == null) mutable.update { it.copy(message = APPLE_RETRY) } else viewModelScope.launch {
                enter(apple = true) {
                    when (val result = client.appleExchange(redirect.code, verifier)) {
                        is AppleSignIn.SignedIn -> result.session
                        is AppleSignIn.NeedsAccount -> {
                            saved[APPLE_LINK_TOKEN] = result.linkToken
                            saved[APPLE_EMAIL] = result.email
                            mutable.update { it.copy(appleEmail = result.email) }
                            null
                        }
                    }
                }
            }
        }
    }

    /** "새 계정으로 시작": a new account holding the Apple account. The link token is single-use, so a failure starts over. */
    suspend fun registerApple() = enter(apple = true) {
        val linkToken = saved.get<String>(APPLE_LINK_TOKEN) ?: return@enter null
        try { client.appleRegister(linkToken) } finally { takeLinkToken() }
    }

    /**
     * "기존 패스키 계정에 연결": passkey sign-in, then the Apple account is linked with the new access token. A closed passkey
     * prompt or failed passkey sign-in keeps the choice open. If the link fails, the passkey tokens are dropped (never stored,
     * logged out best-effort) so the person is not signed in to an account Apple was not linked to, as on iOS. A 401
     * (expired or used link token) or 400 (already linked elsewhere; the server spent the token) also closes the choice.
     */
    suspend fun linkAppleWithPasskey(passkeys: Passkeys) = enter {
        val linkToken = saved.get<String>(APPLE_LINK_TOKEN) ?: return@enter null
        val challenge = client.authenticationOptions()
        val credential = try { passkeys.get(challenge.options) } catch (e: NoCredentialException) {
            throw AppleFailure("이 기기에 Dearby 패스키가 없어요. 새 계정으로 시작해 주세요.")
        } ?: return@enter null
        val tokens = client.authenticate(challenge.id, credential)
        try { client.linkApple(linkToken, tokens) } catch (e: AccountException) {
            runCatching { client.logout(tokens.refreshToken) }
            throw AppleFailure(when (e.error) {
                AccountError.UNAUTHORIZED -> "애플 로그인 시간이 지났어요. 애플로 다시 시작해 주세요.".also { takeLinkToken() }
                AccountError.BAD_REQUEST -> "이미 다른 계정과 연결돼 있어서 연결하지 못했어요.".also { takeLinkToken() }
                else -> "지금은 애플 로그인을 쓸 수 없어요."
            })
        }
        takeLinkToken()
        tokens
    }

    /** Closing the choice drops the link token. */
    fun dismissApple() { takeLinkToken() }

    private fun takeLinkToken(): String? {
        saved.remove<String>(APPLE_EMAIL)
        mutable.update { it.copy(appleEmail = null) }
        return saved.remove<String>(APPLE_LINK_TOKEN)
    }

    /** A failure whose message is shown as is. */
    private class AppleFailure(message: String) : Exception(message)

    /**
     * A closed prompt (null) goes back quietly; anything else that fails says so on the sheet. The finally
     * also ends WORKING when the screen that started it goes away mid-prompt. [apple] picks Apple's 401 and 503 messages.
     */
    private suspend fun enter(apple: Boolean = false, prompt: suspend () -> AccountSession?) {
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
                AccountError.UNAUTHORIZED -> if (apple) APPLE_RETRY else "패스키를 확인하지 못했어요. 다시 시도해 주세요."
                AccountError.RATE_LIMITED -> "요청이 많아요. 잠시 후 다시 시도해 주세요."
                AccountError.TURNED_OFF -> if (apple) "지금은 애플 로그인을 쓸 수 없어요." else "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
                else -> "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
            }
        } catch (e: AppleFailure) {
            failure = e.message
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

    /** "계정 삭제": DELETE /v1/me through [authorized], then this device's tokens are forgotten. A failure keeps them. */
    suspend fun deleteAccount() {
        authorized { client.deleteAccount(it) }
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
