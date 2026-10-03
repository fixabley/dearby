package com.dearby.nativeapp.features.application

import android.content.Intent
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable fun ApplicationBrowser(url: String, close: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    BackHandler { close() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(close) { Text("닫기") }
            TextButton({
                runCatching { require(safeWebUrl(url)); context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                    .onFailure { error = "브라우저를 열지 못했습니다." }
            }) { Text("외부 브라우저") }
        }
        Text("예시 신청 화면입니다. 실제 신청을 받지 않으며, 외부 브라우저에서 예시 링크를 열 수 있어요.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        Text(url.toUri().host.orEmpty(), Modifier.padding(16.dp), style = MaterialTheme.typography.labelMedium)
        error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        Column(Modifier.fillMaxWidth().weight(1f).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("신청 흐름 살펴보기", style = MaterialTheme.typography.headlineMedium)
            Text("이 화면을 닫으면 예시 신청 상태를 선택할 수 있어요. 실제 접수 완료를 뜻하지 않습니다.")
            Text(url, style = MaterialTheme.typography.bodySmall)
        }
    }
}
