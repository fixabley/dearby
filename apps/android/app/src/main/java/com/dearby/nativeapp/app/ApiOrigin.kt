package com.dearby.nativeapp.app

import androidx.annotation.VisibleForTesting
import com.dearby.nativeapp.BuildConfig

/** API origin from the build; Debug instrumentation tests may point it at their own fixture server. */
object ApiOrigin {
    @VisibleForTesting var debugOverride: String? = null
    val current: String get() = debugOverride?.takeIf { BuildConfig.DEBUG } ?: BuildConfig.API_ORIGIN
    /** Debug instrumentation tests keep their sign-in in a vault of their own so it never leaks between tests. */
    @VisibleForTesting var debugSessionName: String? = null
    val sessionName: String get() = debugSessionName?.takeIf { BuildConfig.DEBUG } ?: "account.tokens.v3"
}
