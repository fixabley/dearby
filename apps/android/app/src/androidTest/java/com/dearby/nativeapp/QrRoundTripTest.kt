package com.dearby.nativeapp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.dearby.nativeapp.entities.account.model.ScannedLink
import com.dearby.nativeapp.features.scan.decodeQr
import com.dearby.nativeapp.shared.config.sharedCardUrl
import com.dearby.nativeapp.shared.ui.DearbyQrCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/** The QR the app shows decodes to exactly `<web origin>/s/<UUID>`, which the scanner opens as a share. */
class QrRoundTripTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shownQrDecodesToTheWebShareLinkAndScansBackAsAShare() {
        val web = "https://dearby.wid.io.kr"
        val url = sharedCardUrl("5a1e0000-0000-4000-8000-000000000001", web)
        compose.setContent { Box(Modifier.background(Color.White).padding(32.dp)) { DearbyQrCode(url, Modifier.size(240.dp)) } }
        // The whole window: node captures can be offset under edge-to-edge, clipping the code.
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap().copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        val text = decodeQr(bitmap)
        assertEquals("https://dearby.wid.io.kr/s/5a1e0000-0000-4000-8000-000000000001", text)
        assertFalse(text!!.startsWith("dearby://"))
        assertEquals(ScannedLink.Share("5a1e0000-0000-4000-8000-000000000001"), ScannedLink.parse(text, web))
    }
}
