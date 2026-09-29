package com.dearby.nativeapp.app

import android.os.Bundle
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.dearby.nativeapp.shared.ui.DearbyTheme
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private val incoming = mutableStateOf<String?>(null)
    private val factory by lazy { com.dearby.nativeapp.app.providers.AppViewModelFactory(applicationContext) }
    private val model: DearbyViewModel by viewModels { factory }
    private val catalog: CatalogViewModel by viewModels { factory }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming.value = intent?.dataString
        setContent { DearbyTheme { DiscoveryApp(catalog) } }
    }
    override fun onResume() { super.onResume(); catalog.recheckTime() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); incoming.value = intent.dataString }
}
