package com.dearby.nativeapp.features.calendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.dearby.nativeapp.entities.activity.model.ScheduleModel

/**
 * Permission and device-calendar plumbing for [CalendarConflictSheet] (contract "기기 캘린더 겹침"): `READ_CALENDAR` is
 * requested only here, when the user asks to compare; granted → the overlaps are computed from `CalendarContract` for the
 * schedules' span and held in composition state only; refused → the sheet explains and offers the app settings.
 */
/** [openPrivacy] opens the web privacy policy; the route supplies it so this slice stays free of the browser helper (FSD: no cross-slice import). */
/** [ownLink] is the activity's web link; device events carrying it in their notes (added from Dearby) are not compared. */
@Composable fun CalendarConflictFlow(schedules: List<ScheduleModel>, ownLink: String?, openPrivacy: () -> Unit, close: () -> Unit) {
    val context = LocalContext.current
    fun granted() = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    fun compare(): CalendarAccessState = CalendarAccessState.Compared(compareCalendar(schedules, DeviceCalendarSource(context.contentResolver, ownLink)))
    var access by remember(schedules) { mutableStateOf(if (granted()) compare() else CalendarAccessState.Asking) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        access = if (ok) compare() else CalendarAccessState.Denied
    }
    CalendarConflictSheet(
        schedules, access,
        request = { launcher.launch(Manifest.permission.READ_CALENDAR) },
        openSettings = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))) },
        openPrivacy = openPrivacy,
        close = close,
    )
}
