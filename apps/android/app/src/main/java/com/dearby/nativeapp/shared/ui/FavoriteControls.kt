package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** 즐겨찾기 하트 (contract "즐겨찾기(하트)·새 모집 푸시 알림"): filled = 즐겨찾기됨; the accessibility name is what a tap does. */
@Composable fun FavoriteButton(favorited: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconButton(onClick, modifier) {
        Icon(if (favorited) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (favorited) "즐겨찾기 해제" else "즐겨찾기",
            tint = if (favorited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }

/** `+` 내 활동에 추가 beside an activity heart (contract "`+` 내 활동에 추가"): a check once the activity is in 내 활동. */
@Composable fun AddToMineButton(added: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconButton(onClick, modifier) {
        Icon(if (added) Icons.Rounded.CheckCircle else Icons.Rounded.AddCircleOutline, if (added) "내 활동에 있음" else "내 활동에 추가",
            tint = if (added) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }

/** 알림 수준 of one favorite (contract "프로그램·활동별 알림 수준"): `전체` · `신청일만` (default) · `없음`. */
enum class BellLevel(val label: String, val detail: String, val icon: ImageVector) {
    ALL("전체", "모든 활동 일정과 새 항목", Icons.Rounded.NotificationsActive),
    APPLICATION("신청일만", "모집 시작·모집 마감", Icons.Rounded.NotificationsNone),
    NONE("없음", "알림을 보내지 않아요", Icons.Rounded.NotificationsOff),
}

/** The bell beside a favorited heart; it opens [NotifyLevelSheet]. */
@Composable fun BellButton(level: BellLevel, onClick: () -> Unit, modifier: Modifier = Modifier) =
    IconButton(onClick, modifier) { Icon(level.icon, "알림 ${level.label}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }

/** The bell's choices as a Material 3 bottom sheet of radio rows (like YouTube's channel bell). */
@Composable fun NotifyLevelSheet(title: String, level: BellLevel, choose: (BellLevel) -> Unit, dismiss: () -> Unit) {
    ModalBottomSheet(dismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium)
            BellLevel.entries.forEach { option ->
                ListItem({ Text(option.label) }, Modifier.selectable(option == level, role = Role.RadioButton) { choose(option) },
                    supportingContent = { Text(option.detail) }, leadingContent = { Icon(option.icon, null) },
                    trailingContent = { RadioButton(option == level, null) }, colors = ListItemDefaults.colors(containerColor = Color.Transparent))
            }
        }
    }
}
