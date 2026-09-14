package io.fixabley.dearby.shared.ui.theme

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Native controls · light", showBackground = true)
@Preview(name = "Native controls · dark", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Native controls · large text", fontScale = 2f, widthDp = 360, showBackground = true)
@Composable
private fun NativeControlsPreview() {
    DearbyTheme(dynamicColor = false) {
        Surface {
            Column(Modifier.padding(Spacing.extraLarge), verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
                Text("시스템 컨트롤", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = {}) { Text("주요 행동") }
                OutlinedButton(onClick = {}) { Text("보조 행동") }
                TextButton(onClick = {}) { Text("텍스트 행동") }
                Button(onClick = {}, enabled = false) { Text("사용할 수 없음") }
            }
        }
    }
}
