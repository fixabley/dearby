package com.dearby.nativeapp.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.dearby.nativeapp.shared.ui.DearbyTheme

class MainActivity : ComponentActivity() {
    private val catalog: CatalogViewModel by viewModels()
    private val demo: DemoViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DearbyTheme { DearbyApp(catalog, demo) } }
    }
}
