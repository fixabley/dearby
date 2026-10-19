package com.dearby.nativeapp.widgets.activity.applyPrompt

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.MaterialTheme
import com.dearby.nativeapp.shared.ui.*

/**
 * 공식 사이트에서 돌아왔을 때 묻는 "신청하셨나요?" 시트의 내용(`CenteredMessage` 배치, contract "비스크롤 화면 배치·CTA"): 체크 원·제목·활동 이름·설명 뒤에
 * `신청했어요`(주 버튼)·`아직이에요`(테두리 버튼)·`다시 묻지 않기`(텍스트 버튼)가 세로로 쌓인다. 화면이 ModalBottomSheet로 띄우고 세 선택을 받는다.
 * 신청 표시는 사용자가 직접 남기는 기록이며 주최 측 접수 확인이 아니라는 점을 함께 보인다.
 */
@Composable fun ApplyConfirmationSheet(activityTitle: String, onApplied: () -> Unit, onNotYet: () -> Unit, onNeverAsk: () -> Unit, modifier: Modifier = Modifier) =
    CenteredMessage(Icons.Rounded.CheckCircle, "신청하셨나요?", "신청했다면 내 활동에 표시해 둘게요.", modifier,
        content = {
            Text(activityTitle, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            Text("이 표시는 직접 남기는 기록이며 주최 측 접수 확인은 아니에요.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        },
        primary = MessageAction("신청했어요", onApplied),
        actions = { DearbyOutlineButton(onNotYet, Modifier.fillMaxWidth()) { Text("아직이에요") } },
        secondary = MessageAction("다시 묻지 않기", onNeverAsk))
