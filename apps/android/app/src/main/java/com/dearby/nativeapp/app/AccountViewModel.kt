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
import com.dearby.nativeapp.entities.account.model.WebSignIn
import com.dearby.nativeapp.features.oauth.OAuthBrowser
import com.dearby.nativeapp.features.oauth.OAuthVerifier
import com.dearby.nativeapp.features.passkey.Passkeys
import com.dearby.nativeapp.shared.config.AuthProvider
import com.dearby.nativeapp.shared.config.AuthRedirect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException

enum class AccountPhase { SIGNED_OUT, WORKING, SIGNED_IN }
/** A first-seen Apple or Google account waiting for "new account or link" ([email] may be empty). [auto] is true until the automatic device-passkey attempt has run. */
data class PendingLink(val provider: AuthProvider, val email: String, val auto: Boolean)
/**
 * [pending] is set while a first-seen provider account waits; the new-or-link choice is drawn only after its automatic
 * device-passkey attempt ([AccountViewModel.autoLinkPending]). [googleAvailable] turns false when the server answers 503 to
 * Google's start (contract "구글 로그인": no build flag, the button hides).
 */
data class AccountState(val phase: AccountPhase, val message: String? = null, val pending: PendingLink? = null, val googleAvailable: Boolean = true)
private const val UNSUPPORTED = "이 기기에서는 패스키를 쓸 수 없어요. Android 9 이상이 필요해요."
// SavedStateHandle keys: the provider's page is another app, so these must outlive rotation and process death meanwhile.
private const val WEB_PROVIDER = "web.provider"
private const val WEB_VERIFIER = "web.verifier"
private const val WEB_LINK_TOKEN = "web.linkToken"
private const val WEB_EMAIL = "web.email"

/**
 * Passkey, Apple and Google sign-in (contract "패스키 로그인", "애플 로그인", "구글 로그인"): asked for when publishing or viewing
 * received cards, and offered (never required) on the first-launch intro.
 */
class AccountViewModel(val client: AccountClient, private val vault: SessionStore, private val saved: SavedStateHandle) : ViewModel() {
    private var session: AccountSession? = runCatching { vault.load() }.getOrNull()
    private val refreshing = Mutex()
    private val mutable = MutableStateFlow(AccountState(if (session == null) AccountPhase.SIGNED_OUT else AccountPhase.SIGNED_IN, pending = savedPending()))
    val state = mutable.asStateFlow()

    private fun savedPending(): PendingLink? {
        saved.get<String>(WEB_LINK_TOKEN) ?: return null
        val provider = saved.get<String>(WEB_PROVIDER)?.let { name -> AuthProvider.entries.firstOrNull { it.name == name } } ?: return null
        return PendingLink(provider, saved.get<String>(WEB_EMAIL).orEmpty(), auto = false)
    }

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
     * "Apple로 계속하기" / "Google로 계속하기" (web flow): keeps a new verifier, sends its hash and opens the provider's page in a
     * Custom Tab. Nothing waits here: the result comes back as a `dearby://auth/<provider>` link through [returned], or never if
     * the person leaves the tab, so the phase is SIGNED_OUT again once the tab is open. A 503 for Google hides its button.
     */
    suspend fun startWeb(provider: AuthProvider, browser: OAuthBrowser) = enter(provider) {
        val verifier = OAuthVerifier.create()
        val url = try { client.webStart(provider, OAuthVerifier.hash(verifier)) } catch (e: AccountException) {
            if (e.error == AccountError.TURNED_OFF && provider == AuthProvider.GOOGLE) mutable.update { it.copy(googleAvailable = false) }
            throw e
        }
        if (!url.startsWith("https://")) throw AccountException(AccountError.UNAVAILABLE)
        saved[WEB_PROVIDER] = provider.name
        saved[WEB_VERIFIER] = verifier
        try { browser.open(url) } catch (e: ActivityNotFoundException) {
            saved.remove<String>(WEB_VERIFIER)
            throw WebFailure("브라우저를 열 수 없어요. 브라우저 앱을 확인해 주세요.")
        }
        null
    }

    /** The provider's redirect. The verifier is single-use, so it is dropped whatever comes back; a code without one (or for another provider) is stale. */
    fun returned(redirect: AuthRedirect) {
        val verifier = saved.remove<String>(WEB_VERIFIER)
        val started = saved.get<String>(WEB_PROVIDER)
        val provider = redirect.provider
        val retry = retryMessage(provider)
        when (redirect) {
            is AuthRedirect.Cancelled -> mutable.update { it.copy(message = null) }
            is AuthRedirect.Failed -> mutable.update { it.copy(message = retry) }
            is AuthRedirect.Code -> if (verifier == null || started != provider.name) mutable.update { it.copy(message = retry) } else viewModelScope.launch {
                enter(provider) {
                    when (val result = client.webExchange(provider, redirect.code, verifier)) {
                        is WebSignIn.SignedIn -> result.session
                        is WebSignIn.NeedsAccount -> {
                            saved[WEB_LINK_TOKEN] = result.linkToken
                            saved[WEB_EMAIL] = result.email
                            mutable.update { it.copy(pending = PendingLink(provider, result.email, auto = true)) }
                            null
                        }
                    }
                }
            }
        }
    }

    /** "새 계정으로 시작": a new account holding the provider account. The link token is single-use, so a failure starts over. */
    suspend fun registerPending() {
        val pending = state.value.pending ?: return
        enter(pending.provider) {
            val linkToken = saved.get<String>(WEB_LINK_TOKEN) ?: return@enter null
            try { client.webRegister(pending.provider, linkToken) } finally { takeLinkToken() }
        }
    }

    /**
     * "기존 패스키 계정에 연결" (after the automatic attempt): passkey sign-in, then the provider account is linked with the new
     * access token. A closed passkey prompt or failed passkey sign-in keeps the choice open. If the link fails, the passkey tokens
     * are dropped (never stored, logged out best-effort) so the person is not signed in to an account the provider was not linked
     * to, as on iOS. A 401 (expired or used link token) or 400 (already linked elsewhere; the server spent the token) also closes the choice.
     */
    suspend fun linkPendingWithPasskey(passkeys: Passkeys) {
        val pending = state.value.pending ?: return
        enter(pending.provider) {
            val linkToken = saved.get<String>(WEB_LINK_TOKEN) ?: return@enter null
            val challenge = client.authenticationOptions()
            val credential = try { passkeys.get(challenge.options) } catch (e: NoCredentialException) {
                throw WebFailure("이 기기에 Dearby 패스키가 없어요. 새 계정으로 시작해 주세요.")
            } ?: return@enter null
            linkWith(pending.provider, linkToken, challenge.id, credential)
        }
    }

    /**
     * 2026-10-17 decision, run once right after the 202: a Dearby passkey on this device signs in and links the provider
     * account at once (one prompt, no question); none starts a separate account right away. Closing the passkey prompt
     * leaves the new-or-link choice open for the person to decide.
     */
    suspend fun autoLinkPending(passkeys: Passkeys) {
        val pending = state.value.pending ?: return
        enter(pending.provider) {
            val linkToken = saved.get<String>(WEB_LINK_TOKEN) ?: return@enter null
            val challenge = client.authenticationOptions()
            val credential = try { passkeys.get(challenge.options) } catch (e: NoCredentialException) {
                return@enter try { client.webRegister(pending.provider, linkToken) } finally { takeLinkToken() }
            } ?: return@enter null
            linkWith(pending.provider, linkToken, challenge.id, credential)
        }
        mutable.update { it.copy(pending = it.pending?.copy(auto = false)) }
    }

    private suspend fun linkWith(provider: AuthProvider, linkToken: String, challengeId: String, credential: String): AccountSession {
        val tokens = client.authenticate(challengeId, credential)
        try { client.linkIdentity(provider, linkToken, tokens) } catch (e: AccountException) {
            runCatching { client.logout(tokens.refreshToken) }
            throw WebFailure(when (e.error) {
                AccountError.UNAUTHORIZED -> "${provider.label} 로그인 시간이 지났어요. ${provider.label}로 다시 시작해 주세요.".also { takeLinkToken() }
                AccountError.BAD_REQUEST -> "이미 다른 계정과 연결돼 있어서 연결하지 못했어요.".also { takeLinkToken() }
                else -> offMessage(provider)
            })
        }
        takeLinkToken()
        return tokens
    }

    /** Closing the choice drops the link token. */
    fun dismissPending() { takeLinkToken() }

    private fun takeLinkToken(): String? {
        saved.remove<String>(WEB_EMAIL)
        saved.remove<String>(WEB_PROVIDER)
        mutable.update { it.copy(pending = null) }
        return saved.remove<String>(WEB_LINK_TOKEN)
    }

    private fun retryMessage(provider: AuthProvider) = "${provider.label} 로그인을 마치지 못했어요. 다시 시도해 주세요."
    private fun offMessage(provider: AuthProvider) = "지금은 ${provider.label} 로그인을 쓸 수 없어요."

    /** A failure whose message is shown as is. */
    private class WebFailure(message: String) : Exception(message)

    /**
     * A closed prompt (null) goes back quietly; anything else that fails says so on the sheet. The finally
     * also ends WORKING when the screen that started it goes away mid-prompt. [provider] picks that provider's 401 and 503 messages.
     */
    private suspend fun enter(provider: AuthProvider? = null, prompt: suspend () -> AccountSession?) {
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
                AccountError.UNAUTHORIZED -> provider?.let(::retryMessage) ?: "패스키를 확인하지 못했어요. 다시 시도해 주세요."
                AccountError.RATE_LIMITED -> "요청이 많아요. 잠시 후 다시 시도해 주세요."
                AccountError.TURNED_OFF -> provider?.let(::offMessage) ?: "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
                else -> "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
            }
        } catch (e: WebFailure) {
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
