package io.fixabley.dearby.shared.ui

internal data class BusyOverlayState(val message: String, val intervals: List<BusyInterval> = emptyList(), val window: BusyInterval? = null)
internal data class BusyPlotBlock(val startMinute: Float, val endMinute: Float, val description: String, val timeText: String, val overlaps: Boolean, val overlapStartMinute: Float? = null, val overlapEndMinute: Float? = null, val overlapTimeText: String? = null, val overlapDescription: String? = null)
