package io.fixabley.dearby
import io.fixabley.dearby.pages.noticedetail.model.detailLink
import io.fixabley.dearby.shared.ui.koreanTime
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Test
class DetailLinkTest {
    @Test fun domainDisplayPreservesExactSafeDestination() {
        val url = "https://cieat.cbnu.ac.kr/path?q=room%20501#apply"
        val link = detailLink(url)!!
        assertEquals("cieat.cbnu.ac.kr", link.domain)
        assertEquals(url, link.url)
        listOf(null, "", "javascript:alert(1)", "https://user:pass@example.com", "https:///bad", "https://example.com/has space").forEach { assertNull(detailLink(it)) }
    }
    @Test fun koreanTimeRetainsMinutesAndSeconds() {
        assertEquals("오전 9시", koreanTime(LocalTime.of(9, 0)))
        assertEquals("오후 1시 5분 2초", koreanTime(LocalTime.of(13, 5, 2)))
        assertEquals("오전 12시", koreanTime(LocalTime.MIDNIGHT))
    }
}
