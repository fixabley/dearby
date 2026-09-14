package io.fixabley.dearby.shared.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import java.time.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimelineDatePicker(interval: TimelineInterval, selected: LocalDate, onSelect: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // Material3 stores calendar dates as UTC midnight; source-zone instants are never passed here.
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = selected.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = interval.firstDate.year..interval.lastDate.year,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = interval.contains(
                Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate())
        })
    DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(onClick = {
            picker.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                ?.takeIf(interval::contains)?.let(onSelect)
        }, enabled = picker.selectedDateMillis != null) { Text("선택") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }) { DatePicker(picker) }
}
