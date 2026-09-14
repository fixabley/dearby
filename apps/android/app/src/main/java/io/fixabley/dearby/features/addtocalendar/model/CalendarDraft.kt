package io.fixabley.dearby.features.addtocalendar.model

/** Immutable editor input; launching it never means the user saved an event. */
internal data class CalendarDraft(
    val title: String,
    val description: String,
    val location: String,
    val beginsAtMillis: Long,
    val endsAtMillis: Long,
    val allDay: Boolean,
    val timezone: String,
)
