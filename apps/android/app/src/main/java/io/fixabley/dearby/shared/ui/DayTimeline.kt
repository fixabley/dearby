package io.fixabley.dearby.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import io.fixabley.dearby.shared.ui.theme.Spacing
import java.time.LocalDate

/** Read-only visualization. Selection changes only this view; no calendar mutation or callback. */
@Composable
internal fun DayTimeline(interval: TimelineInterval, title: String, modifier: Modifier = Modifier) {
    var selected by rememberSaveable(interval) { mutableLongStateOf(interval.firstDate.toEpochDay()) }
    val date = LocalDate.ofEpochDay(selected)
    val day = timelineDay(interval, date) ?: return
    var showPicker by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
        TimelineDateHeader(date, interval.adjacent(date, -1) != null, interval.adjacent(date, 1) != null,
            { interval.adjacent(date, -1)?.let { selected = it.toEpochDay() } },
            { interval.adjacent(date, 1)?.let { selected = it.toEpochDay() } }, { showPicker = true })
        Text(day.timeText, Modifier.clearAndSetSemantics { contentDescription = "$title. ${day.description}" },
            style = MaterialTheme.typography.bodyMedium)
        TimelinePlot(day, title)
    }
    if (showPicker) TimelineDatePicker(interval, date, { selected = it.toEpochDay(); showPicker = false }, { showPicker = false })
}
