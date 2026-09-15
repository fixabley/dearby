package io.fixabley.dearby.shared.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import io.fixabley.dearby.shared.ui.buttons.PrimaryButton
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import io.fixabley.dearby.shared.ui.theme.Spacing

@Preview(name = "States · light", widthDp = 360, showBackground = true)
@Preview(name = "States · dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "States · large text", widthDp = 360, fontScale = 2f, showBackground = true)
@Composable
private fun StatusPreview() {
    DearbyTheme(dynamicColor = false) {
        Column(Modifier.padding(Spacing.large), verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
            StatusPanel("아직 내용이 없어요", message = "내용을 추가하면 여기에 표시돼요.")
            StatusPanel("불러오는 중이에요", kind = StatusKind.Loading)
            StatusPanel("불러오지 못했어요", kind = StatusKind.Error,
                action = { PrimaryButton(onClick = {}) { Text("다시 시도") } })
        }
    }
}
