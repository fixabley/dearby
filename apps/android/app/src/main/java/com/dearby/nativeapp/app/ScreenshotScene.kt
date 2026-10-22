package com.dearby.nativeapp.app

import com.dearby.nativeapp.pages.activity.ActivityListPhase
import com.dearby.nativeapp.pages.activity.ProgramPhase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Store screenshots only (apps/android/scripts/capture_screenshots.sh): a Debug launch with the extra `dearbyScreenshot` set to
 * `discover`, `discover-expanded`, `detail`, `mine`, `cards` or `settings` opens that screen. [MainActivity] reads the extra only when
 * `BuildConfig.DEBUG`, so a release build never gets here.
 */
const val SCREENSHOT_EXTRA = "dearbyScreenshot"

/** Expands the first discovery program and returns its first open (else upcoming) activity once loaded; null after 30 s or when none. */
suspend fun ActivityViewModel.screenshotActivity(): String? = withTimeoutOrNull(30_000) {
    val program = state.first { it.phase == ActivityListPhase.LOADED }.programs.firstOrNull() ?: return@withTimeoutOrNull null
    if (program.id !in state.value.expanded) toggle(program.id)
    val loaded = state.first { it.programActivities[program.id]?.phase == ProgramPhase.LOADED }.programActivities.getValue(program.id)
    (loaded.ids + loaded.upcomingIds).firstOrNull()
}
