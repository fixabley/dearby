package io.fixabley.dearby.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import io.fixabley.dearby.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun TimelineDateHeader(date: LocalDate, previousEnabled: Boolean, nextEnabled: Boolean,
    onPrevious: () -> Unit, onNext: () -> Unit, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = previousEnabled, modifier = Modifier.testTag("timeline.previous")) {
            Icon(painterResource(R.drawable.ic_previous), contentDescription = "이전 날짜")
        }
        TextButton(onClick = onPick, modifier = Modifier.weight(1f).testTag("timeline.date")) {
            Text(date.format(DateTimeFormatter.ofPattern("uuuu년 M월 d일 (E)", Locale.KOREAN)))
        }
        IconButton(onClick = onNext, enabled = nextEnabled, modifier = Modifier.testTag("timeline.next")) {
            Icon(painterResource(R.drawable.ic_next), contentDescription = "다음 날짜")
        }
    }
}
