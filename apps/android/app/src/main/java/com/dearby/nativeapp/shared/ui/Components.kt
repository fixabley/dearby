package com.dearby.nativeapp.shared.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable fun DearbyTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF007F80), secondary = Color(0xFF397B78), background = Color.White, surface = Color.White, surfaceVariant = Color(0xFFF0F5F4)), content = content)
@Composable fun FormColumn(content: @Composable ColumnScope.() -> Unit) = Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
@Composable fun Field(label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier, singleLine: Boolean = true) = OutlinedTextField(value, change, modifier.fillMaxWidth(), label = { Text(label) }, singleLine = singleLine)
@Composable fun EmptyPanel(title: String, explanation: String) = FormColumn { Text(title, style = MaterialTheme.typography.headlineMedium); Text(explanation, color = MaterialTheme.colorScheme.onSurfaceVariant) }
