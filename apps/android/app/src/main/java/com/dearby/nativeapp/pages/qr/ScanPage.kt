package com.dearby.nativeapp.pages.qr

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.shared.ui.*

@Composable fun ScanPage(input: String, change: (String) -> Unit, busy: Boolean, show: () -> Unit, camera: () -> Unit, photo: () -> Unit, receive: () -> Unit) {
    FormColumn {
        DearbyLogo(Modifier.align(Alignment.CenterHorizontally))
        Text("명함 교환", style = MaterialTheme.typography.headlineMedium)
        QrModeSwitch(false) { if (it) show() }
        Surface(color = Soft, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.fillMaxWidth().padding(24.dp).heightIn(min = 220.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(80.dp), tint = Teal)
                Text("명함의 QR 코드를 촬영해 주세요.", Modifier.padding(vertical = 20.dp))
                DearbyButton(camera) { Text("카메라로 촬영") }
            }
        }
        DearbyOutlineButton(photo, Modifier.fillMaxWidth()) { Text("QR 사진 선택") }
        Field("명함 링크 붙여넣기", input, change, singleLine = false)
        DearbyButton(receive, Modifier.fillMaxWidth(), enabled = !busy && input.isNotBlank()) { Text("명함 확인") }
        Text("로그인 없이 명함을 저장할 수 있어요. 로그인하지 않고 저장한 명함은 앱을 삭제하면 복구할 수 없어요.", color = Quiet, style = MaterialTheme.typography.bodySmall)
    }
}
