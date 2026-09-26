package com.dearby.nativeapp.pages.qr

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.widgets.card.CardState
import com.dearby.nativeapp.shared.ui.*

@Composable fun QrPage(cards: List<CardState>, selectedId: String?, image: ImageBitmap?, select: (String?) -> Unit, enlarge: () -> Unit, detail: (CardState) -> Unit, create: () -> Unit, share: () -> Unit, copy: () -> Unit, save: () -> Unit, contextLabel: String, contextChange: (String) -> Unit, receive: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val card = cards.find { it.id == selectedId }
    FormColumn {
        Text("명함 교환", style = MaterialTheme.typography.headlineMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { FilledTonalButton({}) { Text("QR 보여주기") }; OutlinedButton(receive) { Text("QR 찍기") } }
        if (card == null) {
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { Text("나를 소개할 새 명함", style = MaterialTheme.typography.titleLarge); Text("공유할 연락처와 활동 이력을 직접 골라 보세요."); Button(create) { Text("새 명함 만들기") } } }
        } else {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(card.title, style = MaterialTheme.typography.titleMedium); Text(card.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                        Box { IconButton({ menu = true }) { Icon(Icons.Outlined.IosShare, "공유 메뉴") }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("링크 공유") }, { menu = false; share() }); DropdownMenuItem({ Text("링크 복사") }, { menu = false; copy() }); DropdownMenuItem({ Text("QR 이미지 저장") }, { menu = false; save() }) } }
                    }
                    image?.let { Image(it, "명함 QR, 누르면 확대", Modifier.fillMaxWidth().aspectRatio(1f).clickable(onClick = enlarge)) }
                    Text("ⓘ QR을 누르면 QR만 크게 보여요", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            Button({ detail(card) }, Modifier.fillMaxWidth()) { Text("명함 보기") }
        }
        Text("내 명함", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OutlinedButton({ select(null) }, Modifier.height(96.dp)) { Text("＋\n새 명함") } }
            items(cards, key = { it.id }) { item -> FilterChip(item.id == selectedId, { select(item.id) }, { Column(Modifier.padding(vertical = 12.dp)) { Text(item.person); Text(item.job); Text(item.title, style = MaterialTheme.typography.labelSmall) } }) }
        }
        Field("교환한 활동 (선택 사항)", contextLabel, contextChange)
        Text("직접 입력한 활동은 참가 인증이 아닙니다.", style = MaterialTheme.typography.bodySmall)
    }
}
