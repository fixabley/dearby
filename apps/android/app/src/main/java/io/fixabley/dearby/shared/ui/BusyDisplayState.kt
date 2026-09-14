package io.fixabley.dearby.shared.ui

internal data class BusyDisplayState(val enabled: Boolean = false, val message: String = "기기 캘린더 연결 안 됨",
    val consent: Boolean = false, val requesting: Boolean = false, val settings: Boolean = false)
internal data class BusyOverlayState(val message: String, val intervals: List<BusyInterval> = emptyList(), val window: BusyInterval? = null)
internal data class BusyPlotBlock(val startMinute: Float, val endMinute: Float, val description: String, val timeText: String, val overlaps: Boolean, val overlapStartMinute: Float? = null, val overlapEndMinute: Float? = null)
