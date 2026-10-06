package com.dearby.nativeapp

import com.dearby.nativeapp.shared.config.sharedCardId
import org.junit.Assert.*
import org.junit.Test

class AppLinksTest {
    private val web = "https://web.example.test"
    private val id = "d0000000-0000-4000-8000-000000000001"

    @Test fun shareLinksOnlyMatchTheWebOriginAndAUuid() {
        assertEquals(id, sharedCardId("$web/s/$id", web))
        assertEquals(id, sharedCardId("$web/s/${id.uppercase()}/", web))
        for (link in listOf("https://other.example.test/s/$id", "http://web.example.test/s/$id",
            "$web/cards/$id", "$web/s/not-a-uuid", "$web/s/1-1-1-1-1", "$web/s/$id/extra",
            "https://web.example.test:8443/s/$id", "$web/s/$id?utm=x", "$web/s/$id#top", "not a url"))
            assertNull(link, sharedCardId(link, web))
    }
    @Test fun buildConfigCarriesAnOrigin() {
        assertTrue(BuildConfig.API_ORIGIN.matches(Regex("^https?://[^/]+$")))
        assertTrue(BuildConfig.WEB_ORIGIN.matches(Regex("^https?://[^/]+$")))
    }
}
