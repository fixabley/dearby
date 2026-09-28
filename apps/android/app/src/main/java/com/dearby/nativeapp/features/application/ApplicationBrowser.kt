package com.dearby.nativeapp.features.application

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@Composable fun ApplicationBrowser(url: String, close: () -> Unit) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var web by remember { mutableStateOf<WebView?>(null) }
    BackHandler { close() }
    DisposableEffect(Unit) { onDispose { web?.stopLoading(); web?.destroy() } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(close) { Text("닫기") }
            TextButton({
                runCatching { require(safeWebUrl(url)); context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    .onFailure { error = "브라우저를 열지 못했습니다." }
            }) { Text("외부 브라우저") }
        }
        Text("외부 로그인은 브라우저에서 진행해야 할 수 있어요. 돌아온 뒤 닫고 신청 여부를 직접 기록해 주세요.", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
        Text(Uri.parse(url).host.orEmpty(), Modifier.padding(16.dp), style = MaterialTheme.typography.labelMedium)
        error?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
        if (safeWebUrl(url)) AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = {
            WebView(it).apply {
                web = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val blocked = !safeWebUrl(request.url.toString())
                        if (blocked) error = "이 로그인 연결은 앱에서 열 수 없습니다. 외부 브라우저를 사용해 주세요."
                        return blocked
                    }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: android.webkit.WebResourceError) {
                        if (request.isForMainFrame) error = "페이지를 불러오지 못했습니다. 네트워크를 확인하거나 외부 브라우저를 사용해 주세요."
                    }
                }
                loadUrl(url)
            }
        }) else Text("안전한 HTTP(S) 주소를 확인할 수 없습니다.", Modifier.padding(20.dp))
    }
}
