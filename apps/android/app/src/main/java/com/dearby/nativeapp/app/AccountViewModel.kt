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
import com.dearby.nativeapp.features.google.GoogleSignIn
import com.dearby.nativeapp.features.oauth.OAuthBrowser
import com.dearby.nativeapp.features.oauth.OAuthVerifier
import com.dearby.nativeapp.features.passkey.Passkeys
import com.dearby.nativeapp.shared.config.AuthProvider
import com.dearby.nativeapp.shared.config.AuthRedirect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
 * device-passkey attempt ([AccountViewModel.autoLinkPending]). [googleAvailable] is false when the build has no
 * `GOOGLE_SERVER_CLIENT_ID` or the server answered 503 to the Google nonce (contract "구글 로그인": the button hides).
 * [linking]/[linkMessage] belong to 설정's 연결하기 (contract "설정"), separate from the sign-in sheet's [message]; [linked] counts
 * successful links so the settings screen reloads its rows.
 */
data class AccountState(val phase: AccountPhase, val message: String? = null, val pending: PendingLink? = null, val googleAvailable: Boolean = true,
    val linking: Boolean = false, val linkMessage: String? = null, val linked: Int = 0)
private const val UNSUPPORTED = "이 기기에서는 패스키를 쓸 수 없어요. Android 9 이상이 필요해요."
// SavedStateHandle keys: the provider's page is another app, so these must outlive rotation and process death meanwhile.
private const val WEB_PROVIDER = "web.provider"
private const val WEB_VERIFIER = "web.verifier"
private const val WEB_LINK_TOKEN = "web.linkToken"
private const val WEB_EMAIL = "web.email"
/** True while Apple's page was opened from 설정 연결하기 (signed in): the redirect then links instead of signing in. */
private const val WEB_LINK_MODE = "web.linkMode"

/**
 * Passkey, Apple and Google sign-in (contract "패스키 로그인", "애플 로그인", "구글 로그인"): asked for when publishing or viewing
 * received cards, and offered (never required) on the first-launch intro. Signed in, the same Apple/Google entry points link the
 * provider to this account instead (contract "설정" 연결하기, [link]). [googleServerClientId] is the build's web OAuth client
 * id (`BuildConfig.GOOGLE_SERVER_CLIENT_ID`); blank hides the Google button.
 */
class AccountViewModel(val client: AccountClient, private val vault: SessionStore, private val saved: SavedStateHandle, private val googleServerClientId: String = "") : ViewModel() {
    private var session: AccountSession? = runCatching { vault.load() }.getOrNull()
    private val refreshing = Mutex()
    private val mutable = MutableStateFlow(AccountState(if (session == null) AccountPhase.SIGNED_OUT else AccountPhase.SIGNED_IN, pending = savedPending(), googleAvailable = googleServerClientId.isNotBlank()))
    val state = mutable.asStateFlow()
    /** The current required consent as the profile last said (contract "개인정보 동의" 2026-10-25); null until read, and again once signed out. */
    private val consentGiven = MutableStateFlow<Boolean?>(null)
    /**
     * Signed in with the consent known and given: only then do the view models sync or send account data (내 활동, 즐겨찾기, 알림 목록, 푸시,
     * 추천, 대기열). Signed in without it they stay in their signed-out mode.
     */
    val ready = combine(mutable, consentGiven) { s, consent -> s.phase == AccountPhase.SIGNED_IN && consent == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    /** Set by [ProfileViewModel] from each profile read (null: unknown, e.g. a failed read). */
    fun consentKnown(given: Boolean?) { consentGiven.value = given }
    init {
        viewModelScope.launch { mutable.collect { if (it.phase != AccountPhase.SIGNED_IN) consentGiven.value = null } }
    }

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

    /**
     * "Google로 계속하기" (contract "구글 로그인", native): a server nonce, then Credential Manager's Google account chooser, then the
     * ID token goes to `/v1/auth/google`; 200 signs in, 202 (first-seen Google account) opens the same new-or-link flow as Apple.
     * A 503 for the nonce hides the button. A closed chooser goes back quietly.
     */
    suspend fun signInWithGoogle(google: GoogleSignIn) {
        // Signed in already (설정 연결하기): the same chooser, but the answer links this account instead of signing in.
        if (session != null) return link(AuthProvider.GOOGLE) { googleResult(google) }
        enter(AuthProvider.GOOGLE) {
            when (val result = googleResult(google)) {
                null -> null
                is WebSignIn.SignedIn -> result.session
                is WebSignIn.NeedsAccount -> {
                    saved[WEB_PROVIDER] = AuthProvider.GOOGLE.name
                    saved[WEB_LINK_TOKEN] = result.linkToken
                    saved[WEB_EMAIL] = result.email
                    mutable.update { it.copy(pending = PendingLink(AuthProvider.GOOGLE, result.email, auto = true)) }
                    null
                }
            }
        }
    }
    /** Nonce → Google chooser → `/v1/auth/google`; null when the chooser was closed. */
    private suspend fun googleResult(google: GoogleSignIn): WebSignIn? {
        val nonce = try { client.googleNonce() } catch (e: AccountException) {
            if (e.error == AccountError.TURNED_OFF) mutable.update { it.copy(googleAvailable = false) }
            throw e
        }
        val idToken = try { google.idToken(googleServerClientId, nonce) } catch (e: NoCredentialException) {
            throw WebFailure("이 기기에 Google 계정이 없어요. 기기 설정에서 Google 계정을 추가해 주세요.")
        } catch (e: GetCredentialException) {
            throw WebFailure(retryMessage(AuthProvider.GOOGLE))
        } ?: return null
        return client.googleSignIn(idToken, nonce)
    }
    /**
     * "Apple로 계속하기" (web flow): keeps a new verifier, sends its hash and opens the provider's page in a Custom Tab. Nothing
     * waits here: the result comes back as a `dearby://auth/<provider>` link through [returned], or never if the person leaves
     * the tab, so the phase is SIGNED_OUT again once the tab is open.
     */
    suspend fun startWeb(provider: AuthProvider, browser: OAuthBrowser) {
        // Signed in already (설정 연결하기): the same page, remembered as a link so the redirect links instead of signing in.
        if (session != null) return link(provider) { openWeb(provider, browser, linkMode = true); null }
        enter(provider) { openWeb(provider, browser, linkMode = false); null }
    }
    private suspend fun openWeb(provider: AuthProvider, browser: OAuthBrowser, linkMode: Boolean) {
        val verifier = OAuthVerifier.create()
        val url = client.webStart(provider, OAuthVerifier.hash(verifier))
        if (!url.startsWith("https://")) throw AccountException(AccountError.UNAVAILABLE)
        saved[WEB_PROVIDER] = provider.name
        saved[WEB_VERIFIER] = verifier
        saved[WEB_LINK_MODE] = linkMode
        try { browser.open(url) } catch (e: ActivityNotFoundException) {
            saved.remove<String>(WEB_VERIFIER)
            saved.remove<Boolean>(WEB_LINK_MODE)
            throw WebFailure("브라우저를 열 수 없어요. 브라우저 앱을 확인해 주세요.")
        }
    }

    /** The provider's redirect. The verifier is single-use, so it is dropped whatever comes back; a code without one (or for another provider) is stale. */
    fun returned(redirect: AuthRedirect) {
        val verifier = saved.remove<String>(WEB_VERIFIER)
        val started = saved.get<String>(WEB_PROVIDER)
        val linkMode = saved.remove<Boolean>(WEB_LINK_MODE) == true
        val provider = redirect.provider
        val retry = retryMessage(provider)
        if (linkMode) {
            // 설정 연결하기: the exchange's 202 link token links this account; a 200 is someone else's account and is dropped.
            saved.remove<String>(WEB_PROVIDER)
            when (redirect) {
                is AuthRedirect.Cancelled -> mutable.update { it.copy(linkMessage = null) }
                is AuthRedirect.Failed -> mutable.update { it.copy(linkMessage = retry) }
                is AuthRedirect.Code -> if (verifier == null || started != provider.name || session == null) mutable.update { it.copy(linkMessage = retry) }
                    else viewModelScope.launch { link(provider) { client.webExchange(provider, redirect.code, verifier) } }
            }
            return
        }
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
        saved.remove<Boolean>(WEB_LINK_MODE)
        mutable.update { it.copy(pending = null) }
        return saved.remove<String>(WEB_LINK_TOKEN)
    }

    /**
     * 설정 연결하기 (contract "설정"): runs a provider prompt while signed in and links its answer to this account with
     * `POST /v1/me/identities/{provider}`. The phase never changes: a 202 link token is spent on the link, a 200 means the provider
     * account already belongs to another Dearby account (its tokens are dropped, best-effort logged out, never kept), and a
     * closed prompt (null) ends quietly. [AccountState.linked] counts successes so 설정 reloads its rows; failures go to [AccountState.linkMessage].
     */
    private suspend fun link(provider: AuthProvider, prompt: suspend () -> WebSignIn?) {
        mutable.update { it.copy(linking = true, linkMessage = null) }
        var failure: String? = null
        try {
            when (val result = prompt()) {
                null -> Unit
                is WebSignIn.SignedIn -> {
                    runCatching { client.logout(result.session.refreshToken) }
                    failure = "이미 다른 Dearby 계정에 연결된 ${provider.label} 계정이에요."
                }
                is WebSignIn.NeedsAccount -> {
                    try { authorized { client.linkIdentity(provider, result.linkToken, it) } } catch (e: AccountException) {
                        throw when (e.error) {
                            AccountError.BAD_REQUEST -> WebFailure(e.detail ?: "이미 다른 계정과 연결돼 있어서 연결하지 못했어요.")
                            AccountError.UNAUTHORIZED -> WebFailure(retryMessage(provider))
                            else -> e
                        }
                    }
                    mutable.update { it.copy(linked = it.linked + 1) }
                }
            }
        } catch (e: Exception) {
            failure = failureMessage(e, provider)
        } finally {
            mutable.update { it.copy(linking = false, linkMessage = failure) }
        }
    }
    fun clearLinkMessage() = mutable.update { it.copy(linkMessage = null) }

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
        } catch (e: Exception) {
            failure = failureMessage(e, provider)
        } finally {
            mutable.update { if (it.phase == AccountPhase.WORKING) it.copy(phase = AccountPhase.SIGNED_OUT, message = failure) else it }
        }
    }
    /** The sentence shown for a failed prompt (sign-in and 설정 연결하기 alike); anything unexpected (cancellation included) is rethrown. */
    private fun failureMessage(e: Exception, provider: AuthProvider?): String = when (e) {
        is GetCredentialUnsupportedException, is CreateCredentialUnsupportedException -> UNSUPPORTED
        is NoCredentialException -> "이 기기에 Dearby 패스키가 없어요. Apple이나 Google로 시작해 주세요."
        is GetCredentialException -> "패스키로 로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
        is CreateCredentialException -> "패스키를 만들지 못했어요. 잠시 후 다시 시도해 주세요."
        is AccountException -> when (e.error) {
            AccountError.UNAUTHORIZED -> provider?.let(::retryMessage) ?: "패스키를 확인하지 못했어요. 다시 시도해 주세요."
            AccountError.RATE_LIMITED -> "요청이 많아요. 잠시 후 다시 시도해 주세요."
            AccountError.TURNED_OFF -> provider?.let(::offMessage) ?: "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
            else -> "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
        }
        is WebFailure -> e.message.orEmpty()
        // A response or credential that is not the contract's JSON.
        is JSONException -> "로그인하지 못했어요. 잠시 후 다시 시도해 주세요."
        else -> throw e
    }

    /** Runs with the session still valid right before 로그아웃 (the push token leaves the server, contract "즐겨찾기(하트)·새 모집 푸시 알림"). */
    var beforeSignOut: suspend (AccountSession) -> Unit = {}
    /** Signing out always forgets this device's tokens, even if the server call fails. */
    suspend fun signOut() {
        session?.let { runCatching { beforeSignOut(it) } }
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
