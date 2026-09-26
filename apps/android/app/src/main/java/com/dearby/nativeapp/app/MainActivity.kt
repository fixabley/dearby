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
    private val model: DearbyViewModel by viewModels { com.dearby.nativeapp.app.providers.AppViewModelFactory(applicationContext) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming.value = intent?.dataString
        setContent { DearbyTheme { DearbyApp(model, incoming.value) { incoming.value = null } } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); incoming.value = intent.dataString }
}
