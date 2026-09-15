package io.fixabley.dearby.pages.noticedetail.ui

import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import io.fixabley.dearby.R
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.fixabley.dearby.features.addtocalendar.model.CalendarDraft

@Composable
internal fun AddToCalendarButton(draft: CalendarDraft?, label: String, onAdd: (CalendarDraft) -> Unit, modifier: Modifier = Modifier) {
    if (draft != null) IconButton(onClick = { onAdd(draft) }, modifier = modifier) { Icon(painterResource(R.drawable.ic_calendar_add), contentDescription = label) }
}
