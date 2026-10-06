package com.dearby.nativeapp

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

fun capturePrototype(rule: ComposeTestRule, name: String) {
    rule.mainClock.advanceTimeBy(500)
    rule.waitForIdle()
    android.os.SystemClock.sleep(350) // Platform dialog animations run outside the Compose clock.
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
        requireNotNull(instrumentation.uiAutomation.takeScreenshot()).compress(Bitmap.CompressFormat.PNG, 100, it)
    }
}
