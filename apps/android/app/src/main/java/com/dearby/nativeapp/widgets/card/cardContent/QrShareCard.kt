package com.dearby.nativeapp.widgets.card.cardContent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

/**
 * 내 코드 화면의 QR 공유 카드. [url]이 준비되면 QR과 주소를 보여 주고, 공유 버튼은 [onShare]로 OS 공유 시트를 연다(화면이 연결).
 * 공유 만들기·로그인·활동 선택 반영은 화면이 맡는다. [url]이 없으면 만드는 중, [errorMessage]가 있으면 실패 상태다.
 */
@Composable fun QrShareCard(
    name: String, job: String, url: String?, onShare: () -> Unit, modifier: Modifier = Modifier,
    errorMessage: String? = null, onRetry: () -> Unit = {},
    activities: List<DearbyChoice> = emptyList(), selectedActivityIds: Set<String> = emptySet(), onToggleActivity: (String) -> Unit = {},
) {
    var showActivities by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(color = Color.White, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(56.dp).background(Teal, CircleShape), contentAlignment = Alignment.Center) {
                    Text(name.take(1), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                }
                Text(name, style = MaterialTheme.typography.titleLarge)
                if (job.isNotBlank()) Text(job, color = Quiet, style = MaterialTheme.typography.bodyMedium)
                Box(Modifier.padding(top = 8.dp).size(220.dp), contentAlignment = Alignment.Center) {
                    when {
                        url != null -> DearbyQrCode(url, Modifier.fillMaxSize().semantics { contentDescription = "명함 QR"; stateDescription = url })
                        errorMessage != null -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(errorMessage, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                            DearbyOutlineButton(onRetry) { Text("다시 시도") }
                        }
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = Teal)
                            Text("QR을 만드는 중이에요", color = Quiet, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (url != null) SelectionContainer { Text(url, color = Quiet, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (url != null) DearbyButton(onShare, Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("공유", style = MaterialTheme.typography.titleMedium)
        }
        if (activities.isNotEmpty()) Column {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = if (showActivities) "접기" else "펼치기", role = Role.Button) { showActivities = !showActivities }
                .semantics(mergeDescendants = true) { stateDescription = if (showActivities) "펼침" else "접힘" }, verticalAlignment = Alignment.CenterVertically) {
                Text("함께 보낼 활동 (선택)", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                if (selectedActivityIds.isNotEmpty()) Text("${selectedActivityIds.size}개", Modifier.padding(end = 4.dp), color = Teal, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelMedium)
                Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.rotate(if (showActivities) 0f else -90f), tint = Quiet)
            }
            AnimatedVisibility(showActivities) {
                DearbyChoiceChips(activities, selectedActivityIds, onToggleActivity, "함께 보낼 활동", Modifier.padding(top = 10.dp))
            }
        }
    }
}

/** 스캔 화면 틀. 카메라 미리보기는 화면이 [preview]로 넣고, 사진에서 고르는 대안을 함께 둔다. */
@Composable fun QrScanOverlay(scanFromPhotos: () -> Unit, modifier: Modifier = Modifier, preview: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        preview()
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(240.dp).border(3.dp, Color.White, RoundedCornerShape(24.dp)))
            Spacer(Modifier.height(20.dp))
            Text("명함 QR을 네모 안에 맞춰 주세요", color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Button(scanFromPhotos, Modifier.padding(bottom = 24.dp).heightIn(min = 44.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal)) {
                Icon(Icons.Outlined.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text("사진에서 스캔")
            }
        }
    }
}
