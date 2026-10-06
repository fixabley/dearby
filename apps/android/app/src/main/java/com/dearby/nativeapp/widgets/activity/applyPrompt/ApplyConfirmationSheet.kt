package com.dearby.nativeapp.widgets.activity.applyPrompt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/**
 * 공식 사이트에서 돌아왔을 때 묻는 "신청하셨나요?" 시트의 내용. 화면이 ModalBottomSheet로 띄우고 세 선택을 받는다.
 * 신청 표시는 사용자가 직접 남기는 기록이며 주최 측 접수 확인이 아니라는 점을 함께 보인다.
 */
@Composable fun ApplyConfirmationSheet(activityTitle: String, onApplied: () -> Unit, onNotYet: () -> Unit, onNeverAsk: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(Modifier.size(48.dp).background(Mint, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.CheckCircle, null, tint = Teal) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("신청하셨나요?", Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
            Text(activityTitle, style = MaterialTheme.typography.titleMedium)
            Text("신청했다면 내 활동에 표시해 둘게요. 이 표시는 직접 남기는 기록이며 주최 측 접수 확인은 아니에요.", color = Quiet, style = MaterialTheme.typography.bodyMedium)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DearbyButton(onApplied, Modifier.fillMaxWidth()) { Text("신청했어요", style = MaterialTheme.typography.titleMedium) }
            DearbyOutlineButton(onNotYet, Modifier.fillMaxWidth()) { Text("아직이에요", style = MaterialTheme.typography.titleMedium) }
            TextButton(onNeverAsk, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("다시 묻지 않기", color = Quiet, fontWeight = FontWeight.SemiBold) }
        }
    }
}
