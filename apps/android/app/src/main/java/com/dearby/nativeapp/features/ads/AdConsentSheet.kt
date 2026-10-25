package com.dearby.nativeapp.features.ads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.DearbyButton

/**
 * 광고 목적 개인정보 제3자 제공 동의(선택) (contract, 2026-10-25): the contract's copy in a Material 3 ModalBottomSheet, from the queue before the
 * first ad request and from 설정's switch. `동의하고 계속` and `동의하지 않음` are the same button at the same size; dismissing is declining.
 * [openFull] shows the privacy policy's 광고 item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun AdConsentSheet(agree: () -> Unit, decline: () -> Unit, openFull: () -> Unit) {
    ModalBottomSheet(decline, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("[선택] 광고 목적 개인정보 제3자 제공 동의", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
            Text("광고를 보여 드려도 될까요?", Modifier.semantics { heading() }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
            Text("맞춤 대기열 끝에 광고 1장을 보여 드리려면 아래 정보를 Google에 제공해야 해요. 맞춤 광고는 아니에요.", style = MaterialTheme.typography.bodyMedium)
            ROWS.forEach { (label, value) ->
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("동의하지 않을 수 있어요. 동의하지 않아도 모든 기능을 그대로 쓸 수 있고, 광고가 표시되지 않을 뿐이에요.", style = MaterialTheme.typography.bodyMedium)
            TextButton(openFull) { Text("전문 보기") }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DearbyButton(decline, Modifier.weight(1f)) { Text("동의하지 않음") }
                DearbyButton(agree, Modifier.weight(1f)) { Text("동의하고 계속") }
            }
        }
    }
}

private val ROWS = listOf(
    "제공받는 자" to "Google LLC(AdMob)",
    "제공 목적" to "앱 안 광고 표시(비개인화), 같은 광고 노출 횟수 제한, 광고 성과 측정, 부정 클릭 방지",
    "제공 항목" to "광고 식별자(광고 ID·앱 세트 ID), IP 주소(대략적 위치 추정), 기기 정보(모델·OS·언어), 광고 상호작용(노출·클릭·동영상 재생), 진단 정보",
    "보유·이용 기간" to "제공받는 자(Google)의 개인정보처리방침에 따름",
    "국외 이전" to "미국(Google LLC), 광고를 요청할 때 네트워크로 전송",
)
