package com.dearby.nativeapp.app

import androidx.lifecycle.ViewModel
import com.dearby.nativeapp.pages.profile.ProfileState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Example profile for the 내 프로필 tab until it reads the account's real profile (#149). */
data class DemoState(val loggedIn: Boolean = false, val profile: ProfileState = demoProfile)
class DemoViewModel : ViewModel() {
    private val mutable = MutableStateFlow(DemoState())
    val state = mutable.asStateFlow()
    fun login(value: Boolean) { mutable.update { it.copy(loggedIn = value) } }
    fun profile(value: ProfileState) { mutable.update { it.copy(profile = value) } }
}
