package com.dearby.nativeapp.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dearby.nativeapp.entities.account.api.AccountError
import com.dearby.nativeapp.entities.account.api.AccountException
import com.dearby.nativeapp.entities.account.model.AccountSession
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [newRecruitment] is the account's 새 모집 알림 switch (on until read); [message] a failed change, shown once. [pushable] is false without Firebase. */
data class NotificationState(val pushable: Boolean, val newRecruitment: Boolean = true, val saving: Boolean = false, val message: String? = null)

/**
 * 새 모집 푸시 (contract "즐겨찾기(하트)·새 모집 푸시 알림", 2026-10-25), signed in only and only after the first program heart. While 새 모집 알림 is on, this device's FCM token goes to
 * `PUT /v1/me/push-devices` with the device time zone at sign-in, at every start, on a new token ([refreshed]), a time-zone change and a granted
 * permission ([register]); turning it off or signing out ([unregister]) removes it. The switch is `/v1/me/notification-settings`: a failed read
 * (404 before the server ships it) leaves it on, a failed save puts it back with [NotificationState.message].
 */
class NotificationViewModel(
    private val account: AccountViewModel, pushable: Boolean, private val token: suspend () -> String?, refreshed: Flow<Unit>,
    /** Push starts at the first program heart ([start]); before it no token is made or sent. */
    private val started: () -> Boolean, private val markStarted: () -> Unit,
) : ViewModel() {
    private val mutable = MutableStateFlow(NotificationState(pushable))
    val state = mutable.asStateFlow()
    private val signedIn get() = account.ready.value

    init {
        viewModelScope.launch {
            account.ready.map { if (it) AccountPhase.SIGNED_IN else AccountPhase.SIGNED_OUT }.collect { phase ->
                if (phase == AccountPhase.SIGNED_IN) load() else mutable.update { NotificationState(it.pushable) }
            }
        }
        viewModelScope.launch { refreshed.collect { register() } }
    }

    private fun load() = viewModelScope.launch {
        val on = try { account.authorized { account.client.newRecruitmentNotifications(it) } } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Log.w("Dearby", "Notification settings read failed: ${(e as? AccountException)?.error ?: e.javaClass.simpleName}")
            true
        }
        mutable.update { it.copy(newRecruitment = on) }
        register()
    }

    /** The first program heart: FCM may now make a token, and it is sent. */
    fun start() { if (!started()) { markStarted(); register() } }

    fun register() {
        if (!signedIn || !started() || !state.value.pushable || !state.value.newRecruitment) return
        viewModelScope.launch {
            try {
                val token = token() ?: return@launch
                account.authorized { account.client.savePushDevice(token, ZoneId.systemDefault().id, it) }
            } catch (e: CancellationException) { throw e } catch (e: Exception) { Log.w("Dearby", "Push device save failed: ${(e as? AccountException)?.error ?: e.javaClass.simpleName}") }
        }
    }

    /** Before the session goes (sign-out): the server forgets this device's token. Best effort. */
    suspend fun unregister(session: AccountSession) {
        if (!state.value.pushable || !started()) return
        runCatching { token()?.let { account.client.deletePushDevice(it, session) } }
    }

    fun setNewRecruitment(on: Boolean) {
        if (!signedIn || state.value.saving || on == state.value.newRecruitment) return
        mutable.update { it.copy(newRecruitment = on, saving = true, message = null) }
        viewModelScope.launch {
            try {
                account.authorized { account.client.saveNewRecruitmentNotifications(on, it) }
                mutable.update { it.copy(saving = false) }
                if (on) register() else token()?.let { t -> runCatching { account.authorized { account.client.deletePushDevice(t, it) } } }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                if ((e as? AccountException)?.error == AccountError.UNAUTHORIZED) return@launch
                mutable.update { it.copy(newRecruitment = !on, saving = false, message = "알림 설정을 바꾸지 못했어요. 잠시 뒤 다시 시도해 주세요.") }
            }
        }
    }
    fun dismissMessage() = mutable.update { it.copy(message = null) }
}
