package io.fixabley.dearby

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.fixabley.dearby.discovery.DiscoveryScreen
import io.fixabley.dearby.ui.theme.DearbyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DearbyTheme {
                DiscoveryScreen()
            }
        }
    }
}
