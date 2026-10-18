package com.dearby.nativeapp.pages.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

enum class MyCardsPhase { SIGNED_OUT, NO_CARD, LOADING, LOADED, FAILED }
/** One of the account's published cards as a row on the 내 명함 tab. */
data class MyCardChoice(val id: String, val title: String, val subtitle: String)
/** 내 명함: the signed-in account's published cards, newest first, or why there are none yet. */
data class MyCardsState(val phase: MyCardsPhase = MyCardsPhase.LOADING, val cards: List<MyCardChoice> = emptyList())

/** 내 명함 tab: lists published cards; [edit] republishes one with its template, [create] makes a new one. */
@Composable fun MyCardsPage(state: MyCardsState, create: () -> Unit, edit: (String) -> Unit, retry: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("내 명함")
        when (state.phase) {
            MyCardsPhase.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Teal) }
            MyCardsPhase.FAILED -> Notice("명함을 불러오지 못했어요", "연결을 확인하고 다시 시도해 주세요.") {
                DearbyOutlineButton(retry, Modifier.fillMaxWidth()) { Text("다시 시도") }
            }
            MyCardsPhase.SIGNED_OUT, MyCardsPhase.NO_CARD -> Column(Modifier.padding(20.dp)) {
                OutlinedCard(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, Line)) {
                    Column(Modifier.fillMaxWidth().heightIn(min = 320.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Outlined.Badge, null, Modifier.size(72.dp), tint = Teal)
                        Spacer(Modifier.height(24.dp))
                        Text("내 명함을\n만드세요.", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Text("프로필 데이터로 명함을 꾸며 발행해요. 발행할 때 패스키로 로그인해요.", Modifier.padding(vertical = 18.dp), color = Quiet, textAlign = TextAlign.Center)
                        DearbyButton(create, Modifier.fillMaxWidth()) { Text("명함 만들기") }
                    }
                }
            }
            MyCardsPhase.LOADED -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { DearbyOutlineButton(create, Modifier.fillMaxWidth()) { Text("＋ 새 명함") } }
                items(state.cards, key = { it.id }) { item ->
                    Surface(onClick = { edit(item.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Line)) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.PersonOutline, null, tint = Teal)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.subtitle, color = Quiet, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "이 명함 고치기", tint = Quiet)
                        }
                    }
                }
                item { Text("명함을 고치면 같은 꾸밈으로 새 명함을 발행해요. 이전 명함은 그대로 남아요.", color = Quiet, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable private fun Notice(title: String, detail: String, action: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, color = Quiet, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        action()
    }
}
