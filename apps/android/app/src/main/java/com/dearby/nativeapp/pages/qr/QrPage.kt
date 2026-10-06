package com.dearby.nativeapp.pages.qr

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*
import com.dearby.nativeapp.widgets.card.cardContent.QrShareCard

/**
 * QR tab. Showing uses the account's real share ([QrShareState]); without a card it offers to make one.
 * Scanning is still the example screen.
 */
@Composable fun QrPage(
    share: QrShareState, activities: List<DearbyChoice>, selectCard: (String) -> Unit, toggleActivity: (String) -> Unit,
    onShare: (String) -> Unit, retry: () -> Unit, create: () -> Unit, scanned: () -> Unit,
) {
    var scanning by remember { mutableStateOf(false) }
    var light by remember { mutableStateOf(false) }
    FormColumn {
        DearbyLogo(Modifier.align(Alignment.CenterHorizontally))
        Text("명함 교환", style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth().background(Soft, RoundedCornerShape(12.dp))) {
            listOf(false to "QR 보여주기", true to "QR 찍기").forEach { (value, label) ->
                TextButton({ scanning = value }, Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(11.dp), colors = ButtonDefaults.textButtonColors(containerColor = if (scanning == value) Teal else Color.Transparent, contentColor = if (scanning == value) Color.White else Quiet)) { Text(label, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (scanning) {
            Box(Modifier.fillMaxWidth().height(365.dp).background(Color(0xFF34393B), RoundedCornerShape(14.dp)).clickable(onClick = scanned)) {
                Icon(Icons.Outlined.CropFree, null, Modifier.size(220.dp).align(Alignment.Center), tint = Color.White)
                Text("명함의 QR 코드를 화면에 맞춰주세요.", Modifier.align(Alignment.Center).padding(20.dp), color = Color.White, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                IconButton({ light = !light }, Modifier.align(Alignment.BottomCenter).padding(16.dp)) { Icon(if (light) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff, "예시 조명 전환", tint = Color.White) }
            }
            DearbyOutlineButton(scanned, Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.width(8.dp)); Text("사진에서 선택") }
            Text("카메라를 켜지 않는 예시예요. 화면을 누르면 명함을 볼 수 있어요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
        } else if (share.phase == QrSharePhase.SIGNED_OUT || share.phase == QrSharePhase.NO_CARD) {
            OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Line)) {
                Column(Modifier.fillMaxWidth().heightIn(min = 320.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.Badge, null, Modifier.size(72.dp), tint = Teal)
                    Spacer(Modifier.height(24.dp))
                    Text("이번에 공유할\n명함을 만드세요.", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Text("명함을 만들면 QR로 바로 건넬 수 있어요. 발행할 때 이메일로 로그인해요.", Modifier.padding(vertical = 18.dp), color = Quiet, textAlign = TextAlign.Center)
                    DearbyButton(create, Modifier.fillMaxWidth()) { Text("명함 만들기") }
                }
            }
        } else {
            QrShareCard(share.name, share.job, share.url, { share.url?.let(onShare) },
                errorMessage = share.error, onRetry = retry,
                activities = activities, selectedActivityIds = share.activityIds, onToggleActivity = toggleActivity)
            Text("내 명함", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { OutlinedButton(create, Modifier.width(112.dp).height(116.dp), shape = RoundedCornerShape(12.dp)) { Text("＋\n새 명함", textAlign = TextAlign.Center) } }
                items(share.cards, key = { it.id }) { item ->
                    val chosen = item.id == share.selectedCardId
                    Surface(onClick = { selectCard(item.id) }, modifier = Modifier.width(112.dp).heightIn(min = 116.dp), color = if (chosen) Mint else Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(if (chosen) 1.5.dp else 1.dp, if (chosen) Teal else Line)) {
                        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Outlined.PersonOutline, null, tint = Teal)
                            Text(item.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.subtitle, color = Quiet, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
