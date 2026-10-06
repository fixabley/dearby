package com.dearby.nativeapp.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.dearby.nativeapp.BuildConfig
import com.dearby.nativeapp.shared.config.sharedCardId
import com.dearby.nativeapp.shared.ui.DearbyTheme

class MainActivity : ComponentActivity() {
    private val catalog: CatalogViewModel by viewModels()
    private val demo: DemoViewModel by viewModels()
    // Captured from /s/<UUID> App Links; the shared-card screen is connected in a later step.
    private var incomingShareId by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        receive(intent)
        setContent {
            DearbyTheme {
                DearbyApp(catalog, demo)
                if (incomingShareId != null) AlertDialog(
                    onDismissRequest = { incomingShareId = null },
                    title = { Text("공유 명함 링크") },
                    text = { Text("공유 명함 화면은 아직 연결되지 않았어요.") },
                    confirmButton = { TextButton({ incomingShareId = null }) { Text("확인") } },
                )
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }
    private fun receive(intent: Intent?) {
        intent?.dataString?.let { incomingShareId = sharedCardId(it, BuildConfig.WEB_ORIGIN) }
    }
}
