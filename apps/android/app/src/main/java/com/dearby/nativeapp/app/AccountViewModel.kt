package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import com.dearby.nativeapp.entities.account.api.AccountClient
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.api.SessionStore
import com.dearby.nativeapp.entities.account.model.AccountSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AccountPhase { SIGNED_OUT, SENDING_CODE, CODE_SENT, VERIFYING, SIGNED_IN }
/** [codeSentAt] is when the last code was sent (epoch ms), for the one-per-minute resend wait. */
data class AccountState(val phase: AccountPhase, val email: String = "", val message: String? = null, val codeSentAt: Long? = null)

/** Email-code sign-in, asked for only when publishing, sharing or saving a received card. */
class AccountViewModel(val client: AccountClient, private val vault: SessionStore) : ViewModel() {
    private var session: AccountSession? = runCatching { vault.load() }.getOrNull()
    private var challengeId: String? = null
    private val mutable = MutableStateFlow(AccountState(if (session == null) AccountPhase.SIGNED_OUT else AccountPhase.SIGNED_IN))
    val state = mutable.asStateFlow()

    suspend fun requestCode(address: String) {
        val email = address.trim().lowercase()
        if ('@' !in email || email.length > 254) { mutable.update { it.copy(message = "이메일 주소를 확인해 주세요.") }; return }
        mutable.update { it.copy(phase = AccountPhase.SENDING_CODE, message = null) }
        try {
            challengeId = client.requestCode(email)
            mutable.update { it.copy(phase = AccountPhase.CODE_SENT, email = email, codeSentAt = System.currentTimeMillis()) }
        } catch (e: AccountException) {
            mutable.update { it.copy(phase = if (challengeId == null) AccountPhase.SIGNED_OUT else AccountPhase.CODE_SENT,
                message = text(e, "인증번호를 보내지 못했어요. 잠시 후 다시 시도해 주세요.")) }
        }
    }
    suspend fun verify(input: String) {
        val code = input.trim()
        val challenge = challengeId
        if (challenge == null || code.length != 6 || !code.all { it in '0'..'9' }) {
            mutable.update { it.copy(message = "인증번호 6자리를 입력해 주세요.") }; return
        }
        mutable.update { it.copy(phase = AccountPhase.VERIFYING, message = null) }
        try {
            val signedIn = client.signIn(challenge, code)
            vault.save(signedIn)
            session = signedIn
            challengeId = null
            mutable.update { it.copy(phase = AccountPhase.SIGNED_IN, codeSentAt = null) }
        } catch (e: AccountException) {
            mutable.update { it.copy(phase = AccountPhase.CODE_SENT, message =
                if (e.error == AccountError.UNAUTHORIZED) "인증번호가 맞지 않거나 만료됐어요." else text(e, "로그인하지 못했어요. 잠시 후 다시 시도해 주세요.")) }
        }
    }
    /** Back to the email step, e.g. to fix a mistyped address. */
    fun changeEmail() {
        challengeId = null
        mutable.update { it.copy(phase = AccountPhase.SIGNED_OUT, message = null, codeSentAt = null) }
    }
    /** Signing out always forgets the local session, even if the server call fails. */
    suspend fun signOut() {
        session?.let { runCatching { client.signOut(it) } }
        forget()
    }
    /** Runs an owner call; an expired or revoked session signs out instead of retrying. */
    suspend fun <T> authorized(call: suspend (AccountSession) -> T): T {
        val current = session ?: throw AccountException(AccountError.UNAUTHORIZED)
        try { return call(current) } catch (e: AccountException) {
            if (e.error == AccountError.UNAUTHORIZED) { forget(); mutable.update { it.copy(message = "로그인이 만료됐어요. 다시 로그인해 주세요.") } }
            throw e
        }
    }
    private fun forget() {
        runCatching { vault.clear() }
        session = null
        challengeId = null
        mutable.update { it.copy(phase = AccountPhase.SIGNED_OUT) }
    }
    private fun text(e: AccountException, fallback: String) = when (e.error) {
        AccountError.RATE_LIMITED -> "요청이 많아요. 잠시 후 다시 시도해 주세요."
        AccountError.INVALID_INPUT -> "이메일 주소를 확인해 주세요."
        else -> fallback
    }
}
