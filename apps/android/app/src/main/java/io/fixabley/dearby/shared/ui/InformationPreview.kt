package io.fixabley.dearby.shared.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import io.fixabley.dearby.R
import androidx.compose.ui.tooling.preview.Preview
import io.fixabley.dearby.shared.ui.buttons.SecondaryButton
import io.fixabley.dearby.shared.ui.theme.DearbyTheme
import io.fixabley.dearby.shared.ui.theme.Spacing

@Preview(name = "Information · light", widthDp = 360, showBackground = true)
@Preview(name = "Information · dark", widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Information · large text", widthDp = 360, fontScale = 2f, showBackground = true)
@Composable
private fun InformationPreview() {
    DearbyTheme(dynamicColor = false) {
        Column(Modifier.padding(Spacing.large), verticalArrangement = Arrangement.spacedBy(Spacing.large)) {
            ContentSection {
                InformationRow("안내", "여러 줄로 이어지는 정보도 글자 크기에 맞춰 읽을 수 있어요.")
                MetadataRow(painterResource(R.drawable.ic_calendar), "2026.9.15(화) 14:00–16:00", "기간: 2026년 9월 15일 오후 2시부터 4시, 한국 시간")
                SecondaryButton(onClick = {}) { Text("자세히 보기") }
            }
        }
    }
}
