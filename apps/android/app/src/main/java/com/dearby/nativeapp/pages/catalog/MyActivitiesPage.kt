package com.dearby.nativeapp.pages.catalog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/** Applied activities in schedule order, each with the user's own participation mark. */
@Composable fun MyActivitiesPage(activities: List<ActivityState>, open: (String) -> Unit, confirm: (String, Boolean) -> Unit, explore: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("내 활동", style = MaterialTheme.typography.headlineMedium)
            ExampleBadge()
        } }
        if (activities.isEmpty()) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("신청한 활동이 없어요. 발견에서 관심 있는 활동을 신청해 보세요.", color = Quiet)
                DearbyOutlineButton(explore) { Text("활동 둘러보기") }
            }
        } else item { Text(CONFIRM_NOTE, color = Quiet, style = MaterialTheme.typography.bodySmall) }
        items(activities, key = { it.id }) { activity ->
            ActivityCard(activity, open) {
                ExampleBadge(if (activity.confirmed) "참여 확정" else "신청함", accent = activity.confirmed)
                ConfirmToggle(activity) { confirm(activity.id, it) }
            }
        }
    }
}

internal const val CONFIRM_NOTE = "참여 확정 표시는 내가 남기는 예시 표시예요. 주최 측 확정이 아니에요."

@Composable internal fun ConfirmToggle(activity: ActivityState, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(activity.confirmed, role = Role.Switch, onValueChange = change),
        verticalAlignment = Alignment.CenterVertically) {
        Text("참여 확정 표시", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(activity.confirmed, null)
    }
}
