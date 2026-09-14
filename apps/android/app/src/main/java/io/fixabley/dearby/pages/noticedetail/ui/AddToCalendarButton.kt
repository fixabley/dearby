package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft

@Composable
internal fun AddToCalendarButton(draft: CalendarDraft?, label: String, onAdd: (CalendarDraft) -> Unit, modifier: Modifier = Modifier) {
    if (draft != null) OutlinedButton(onClick = { onAdd(draft) }, modifier = modifier) { Text(label) }
}
