package io.fixabley.dearby

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag

/** Asset/Room work is outside Compose idling; wait for an actual published snapshot. */
internal fun ComposeTestRule.waitForCatalog() {
    waitUntil(10_000) { onAllNodesWithTag("catalog.ready").fetchSemanticsNodes().isNotEmpty() }
}
